package me.yuugao.pullhire.selection.repo;

import java.util.List;

import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.ingest.CardUpdatedEvent;
import me.yuugao.pullhire.selection.ingest.CardUpdatedListener;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedEvent;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedListener;
import me.yuugao.pullhire.selection.repo.CandidateCardRepository;
import me.yuugao.pullhire.selection.repo.CandidateCategoryRepository;
import me.yuugao.pullhire.selection.repo.CandidateSearchRepository;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import me.yuugao.pullhire.selection.web.dto.CandidateCardResponse;
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
 * Exit criterion 2 (selection.md §2): a candidate with a card but without
 * {@code category_assigned} is NOT returned by search; after
 * {@code category_assigned} the candidate becomes searchable with the
 * {@code categoryConfirmed} derivation ({@code cards.grade ==
 * categories.grade}); {@code visible_to_employers=false} removes the
 * candidate from search.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code SELECTION_TEST_DB_URL}; otherwise
 * the class is skipped. Listeners are invoked directly (no RabbitMQ, no
 * Spring context, no Testcontainers).
 */
class CandidateSearchPostgresDbTest {

    private static String url;

    private CardUpdatedListener cards;
    private CategoryAssignedListener categories;
    private CandidateSearchRepository search;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("SELECTION_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "SELECTION_TEST_DB_URL not set: DB-backed search checks skipped");
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
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE candidate_cards, candidate_categories, rank_signals, "
                + "processed_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        EventIdDedup dedup = new EventIdDedup(jdbc);
        ProcessedEventRepository processedEvents = new ProcessedEventRepository(jdbc);
        categories = new CategoryAssignedListener(dedup, processedEvents,
                new CandidateCategoryRepository(jdbc));
        cards = new CardUpdatedListener(dedup, processedEvents,
                new CandidateCardRepository(named));
        search = new CandidateSearchRepository(named);
    }

    @Test
    void candidateWithoutCategoryIsNotSearchable() {
        // 1. Card only, no category_assigned -> not returned by search (any filter combination).
        cards.onCardUpdated(cardUpdated("evt-card-1", "42", true, "Ivan P.",
                "backend", "middle", List.of("Java", "PostgreSQL")));
        assertThat(searchAll()).isEmpty();
        assertThat(search.search("backend", null, null, null, null, 20, 0)).isEmpty();
        assertThat(search.search(null, "middle", null, null, null, 20, 0)).isEmpty();
        assertThat(search.search(null, null, "backend-middle", null, null, 20, 0)).isEmpty();
        assertThat(search.search("backend", "middle", "backend-middle",
                new String[]{"Java"}, null, 20, 0)).isEmpty();
        assertThat(search.count(null, null, null, null, null)).isZero();

        // 2a. category_assigned with matching grade -> searchable, categoryConfirmed=true.
        categories.onCategoryAssigned(categoryAssigned("evt-cat-1", "42", "backend", "middle",
                "backend-middle", "sess-1", "2026-10-01T00:00:00Z"));
        List<CandidateCardResponse> rows = searchAll();
        assertThat(rows).hasSize(1);
        CandidateCardResponse row = rows.get(0);
        assertThat(row.candidateId()).isEqualTo(42L);
        assertThat(row.categoryConfirmed()).isTrue();
        assertThat(row.categoryGrade()).isEqualTo("middle");

        // 2b. Second candidate whose declared grade differs from assigned -> categoryConfirmed=false.
        cards.onCardUpdated(cardUpdated("evt-card-2", "43", true, "Anna K.",
                "backend", "middle", List.of("Java")));
        categories.onCategoryAssigned(categoryAssigned("evt-cat-2", "43", "backend", "senior",
                "backend-senior", "sess-2", "2026-10-02T00:00:00Z"));
        List<CandidateCardResponse> all = searchAll();
        assertThat(all).hasSize(2);
        CandidateCardResponse other = byCandidateId(all, 43L);
        assertThat(other.categoryConfirmed()).isFalse();
        assertThat(other.categoryGrade()).isEqualTo("senior");

        // 3. visible_to_employers=false card_updated -> candidate disappears from search.
        cards.onCardUpdated(cardUpdated("evt-card-3", "42", false, "Ivan P.",
                "backend", "middle", List.of("Java", "PostgreSQL")));
        List<CandidateCardResponse> afterHide = searchAll();
        assertThat(candidateIds(afterHide)).doesNotContain(42L);
        assertThat(candidateIds(afterHide)).contains(43L);
    }

    private List<CandidateCardResponse> searchAll() {
        return search.search(null, null, null, null, null, 20, 0);
    }

    private static CandidateCardResponse byCandidateId(List<CandidateCardResponse> rows, long candidateId) {
        return rows.stream().filter(r -> r.candidateId() == candidateId).findFirst().orElseThrow();
    }

    private static List<Long> candidateIds(List<CandidateCardResponse> rows) {
        return rows.stream().map(CandidateCardResponse::candidateId).toList();
    }

    private static CategoryAssignedEvent categoryAssigned(String eventId, String candidateId,
            String spec, String grade, String categoryCode, String sessionId, String assignedAt) {
        return new CategoryAssignedEvent(eventId, CategoryAssignedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "assessment-service", null,
                new CategoryAssignedEvent.Payload(candidateId, spec, grade, categoryCode,
                        sessionId, assignedAt));
    }

    private static CardUpdatedEvent cardUpdated(String eventId, String candidateId, boolean visible,
            String displayName, String spec, String grade, List<String> stack) {
        return new CardUpdatedEvent(eventId, CardUpdatedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "candidate-service", null,
                new CardUpdatedEvent.Payload(candidateId, visible, displayName, null, null,
                        spec, grade, stack, false, false));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
