package me.yuugao.pullhire.selection;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.application.RankingService;
import me.yuugao.pullhire.selection.application.SelectionService;
import me.yuugao.pullhire.selection.config.SelectionProperties;
import me.yuugao.pullhire.selection.ingest.CardUpdatedEvent;
import me.yuugao.pullhire.selection.ingest.CardUpdatedListener;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedEvent;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedListener;
import me.yuugao.pullhire.selection.repo.CandidateCardRepository;
import me.yuugao.pullhire.selection.repo.CandidateCategoryRepository;
import me.yuugao.pullhire.selection.repo.CandidateSearchRepository;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import me.yuugao.pullhire.selection.repo.RankSignalRepository;
import me.yuugao.pullhire.selection.repo.SelectionResultRepository;
import me.yuugao.pullhire.selection.repo.TaxonomyRepository;
import me.yuugao.pullhire.selection.web.dto.SelectionItemResponse;
import me.yuugao.pullhire.selection.web.dto.SelectionRequest;
import me.yuugao.pullhire.selection.web.dto.SelectionResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rank invariance to non-ranking inputs (exit criterion 4,
 * {@code docs/selection.md §3}): resume / stack-as-position-signal /
 * freshness ({@code ProfileFreshness}) are not ranking inputs — resume and
 * stack act only as filters, freshness does not affect position.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code SELECTION_TEST_DB_URL}; otherwise
 * the class is skipped. Listeners and services are invoked directly
 * (no Spring context, no RabbitMQ, no Testcontainers).
 */
class RankInvarianceFlowTest {

    private static final long CANDIDATE_A = 101L;
    private static final long CANDIDATE_B = 102L;
    private static final long CANDIDATE_C = 103L;
    private static final long CANDIDATE_D = 104L;
    private static final long CANDIDATE_E = 105L;
    private static final String CATEGORY = "backend-middle";

    private static String url;

    private JdbcTemplate jdbc;
    private CategoryAssignedListener categories;
    private CardUpdatedListener cards;
    private SelectionService selections;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("SELECTION_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "SELECTION_TEST_DB_URL not set: DB-backed flow checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("selection")
                .defaultSchema("selection")
                .locations("classpath:db/migration/selection")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE candidate_cards, candidate_categories, rank_signals, "
                + "processed_events, selection_results RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        EventIdDedup dedup = new EventIdDedup(jdbc);
        ProcessedEventRepository processedEvents = new ProcessedEventRepository(jdbc);
        categories = new CategoryAssignedListener(dedup, processedEvents,
                new CandidateCategoryRepository(jdbc));
        cards = new CardUpdatedListener(dedup, processedEvents,
                new CandidateCardRepository(named));
        SelectionProperties properties = new SelectionProperties();
        selections = new SelectionService(
                new TaxonomyRepository(jdbc),
                new CandidateSearchRepository(named),
                new RankSignalRepository(jdbc),
                new SelectionResultRepository(named),
                new RankingService(properties),
                properties);
        seedTaxonomy();
    }

    @Test
    void resumeAndStackAreFiltersOnlyAndFreshnessDoesNotAffectPosition() {
        seedCandidates();

        // 1. Stack-is-filter: baseline without stack filter includes all, ordered by theta.
        SelectionResponse baseline = selections.create(request(null));
        assertThat(ids(baseline)).containsExactly(
                CANDIDATE_A, CANDIDATE_C, CANDIDATE_B, CANDIDATE_D, CANDIDATE_E);
        assertThat(positions(baseline)).containsExactly(
                Map.entry(CANDIDATE_A, 1), Map.entry(CANDIDATE_C, 2),
                Map.entry(CANDIDATE_B, 3), Map.entry(CANDIDATE_D, 4),
                Map.entry(CANDIDATE_E, 5));

        // Filter matching C and B narrows the set without reordering survivors:
        // relative order in the filtered response equals baseline-relative order.
        SelectionResponse filtered = selections.create(request(List.of("Go")));
        assertThat(ids(filtered)).containsExactly(CANDIDATE_C, CANDIDATE_B);
        List<Long> survivorsInBaselineOrder = ids(baseline).stream()
                .filter(ids(filtered)::contains)
                .toList();
        assertThat(ids(filtered)).containsExactlyElementsOf(survivorsInBaselineOrder);

        // 2. Position-invariance on stack change: B's stack extended, positions in the
        // unchanged (unfiltered) set stay the same — stack changed membership only.
        cards.onCardUpdated(cardUpdated("evt-card-b2", CANDIDATE_B, List.of("Go", "Kubernetes")));
        SelectionResponse afterStackChange = selections.create(request(null));
        assertThat(positions(afterStackChange)).isEqualTo(positions(baseline));
        SelectionResponse filteredAgain = selections.create(request(List.of("Go")));
        assertThat(ids(filteredAgain)).containsExactly(CANDIDATE_C, CANDIDATE_B);

        // 3. Freshness-no-input: bump updated_at directly, positions must be identical.
        jdbc.update("UPDATE candidate_cards SET updated_at = now() + interval '30 days'"
                + " WHERE candidate_id = ?", CANDIDATE_B);
        SelectionResponse afterFreshnessTouch = selections.create(request(null));
        assertThat(positions(afterFreshnessTouch)).isEqualTo(positions(baseline));

        // 4. Resume-no-input: RankingService input record carries no resume-derived field.
        Set<String> fields = Arrays.stream(RankingService.RankInput.class.getRecordComponents())
                .map(c -> c.getName().toLowerCase())
                .collect(Collectors.toSet());
        List<String> blacklist = List.of("resume", "freshness", "headline", "summary");
        assertThat(fields).noneMatch(name -> blacklist.stream().anyMatch(name::contains));

        // 5. Consistency: unchanged signals -> identical positions across repeated create() calls.
        SelectionResponse repeat = selections.create(request(null));
        assertThat(positions(repeat)).isEqualTo(positions(baseline));
        assertThat(ids(repeat)).containsExactly(
                CANDIDATE_A, CANDIDATE_C, CANDIDATE_B, CANDIDATE_D, CANDIDATE_E);

        // 6. Tie-break: D and E share identical theta/FSP inputs (same category,
        // both confirmed) so the equal-score pair orders by candidate_id ascending.
        List<Long> tieIds = ids(repeat).stream()
                .filter(id -> id == CANDIDATE_D || id == CANDIDATE_E)
                .toList();
        assertThat(tieIds).containsExactly(CANDIDATE_D, CANDIDATE_E);
    }

    private void seedCandidates() {
        categories.onCategoryAssigned(categoryAssigned("evt-cat-a", CANDIDATE_A));
        categories.onCategoryAssigned(categoryAssigned("evt-cat-b", CANDIDATE_B));
        categories.onCategoryAssigned(categoryAssigned("evt-cat-c", CANDIDATE_C));
        categories.onCategoryAssigned(categoryAssigned("evt-cat-d", CANDIDATE_D));
        categories.onCategoryAssigned(categoryAssigned("evt-cat-e", CANDIDATE_E));
        cards.onCardUpdated(cardUpdated("evt-card-a", CANDIDATE_A, List.of()));
        cards.onCardUpdated(cardUpdated("evt-card-b", CANDIDATE_B, List.of("Go")));
        cards.onCardUpdated(cardUpdated("evt-card-c", CANDIDATE_C, List.of("Go", "Java")));
        cards.onCardUpdated(cardUpdated("evt-card-d", CANDIDATE_D, List.of()));
        cards.onCardUpdated(cardUpdated("evt-card-e", CANDIDATE_E, List.of()));
        upsertTheta(CANDIDATE_A, 2.0);
        upsertTheta(CANDIDATE_C, 1.5);
        upsertTheta(CANDIDATE_B, 1.0);
        upsertTheta(CANDIDATE_D, 0.5);
        upsertTheta(CANDIDATE_E, 0.5);
    }

    private void upsertTheta(long candidateId, double theta) {
        jdbc.update("INSERT INTO rank_signals"
                + " (candidate_id, category_code, theta, fsp_verified,"
                + " fsp_achievements_count, fsp_achievements_weighted, updated_at)"
                + " VALUES (?, ?, ?, false, 0, 0, now())"
                + " ON CONFLICT (candidate_id, category_code) DO UPDATE SET"
                + " theta = EXCLUDED.theta, updated_at = now()",
                candidateId, CATEGORY, theta);
    }

    private void seedTaxonomy() {
        jdbc.update("INSERT INTO taxonomy (kind, code, name, grade_order, is_active)"
                + " VALUES ('specialization', 'backend', 'Backend', NULL, true)"
                + " ON CONFLICT (kind, code) DO UPDATE SET is_active = true");
        jdbc.update("INSERT INTO taxonomy (kind, code, name, grade_order, is_active)"
                + " VALUES ('grade', 'middle', 'Middle', 1, true)"
                + " ON CONFLICT (kind, code) DO UPDATE SET is_active = true");
    }

    private static SelectionRequest request(List<String> stack) {
        return new SelectionRequest("need", "desc", "backend", "middle", stack, null);
    }

    private static List<Long> ids(SelectionResponse response) {
        return response.items().stream().map(SelectionItemResponse::candidateId).toList();
    }

    private static Map<Long, Integer> positions(SelectionResponse response) {
        Map<Long, Integer> positions = new LinkedHashMap<>();
        for (SelectionItemResponse item : response.items()) {
            positions.put(item.candidateId(), item.position());
        }
        return positions;
    }

    private static CategoryAssignedEvent categoryAssigned(String eventId, long candidateId) {
        return new CategoryAssignedEvent(eventId, CategoryAssignedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "assessment-service", null,
                new CategoryAssignedEvent.Payload(String.valueOf(candidateId), "backend", "middle",
                        CATEGORY, "sess-" + candidateId, "2026-10-01T00:00:00Z"));
    }

    private static CardUpdatedEvent cardUpdated(String eventId, long candidateId, List<String> stack) {
        return new CardUpdatedEvent(eventId, CardUpdatedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "candidate-service", null,
                new CardUpdatedEvent.Payload(String.valueOf(candidateId), true, "Cand " + candidateId,
                        "Backend developer", null, "backend", "middle", stack, false, false));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
