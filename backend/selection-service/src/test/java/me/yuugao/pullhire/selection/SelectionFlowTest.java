package me.yuugao.pullhire.selection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.yuugao.pullhire.selection.application.RankingService;
import me.yuugao.pullhire.selection.application.SelectionService;
import me.yuugao.pullhire.selection.config.SelectionProperties;
import me.yuugao.pullhire.selection.error.ApiException;
import me.yuugao.pullhire.selection.repo.CandidateSearchRepository;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full {@code POST /selections} path (exit criterion 3,
 * {@code docs/selection.md §4.3}): {@code SelectionService.create} over a
 * seeded pool returns a подборка where every item carries an inclusion basis
 * plus a position explanation (all four blocks non-null), the unconfirmed
 * candidate ranks below confirmed ones, {@code theta} is never disclosed,
 * unknown taxonomy fails with {@code ApiException} (OQ-27), and the
 * {@code selection_results} snapshot round-trips via {@code getById}.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code SELECTION_TEST_DB_URL}; otherwise
 * the class is skipped. No Spring context, no Testcontainers.
 */
class SelectionFlowTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private JdbcTemplate jdbc;
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
                + "selection_results, processed_events, taxonomy RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        SelectionProperties properties = new SelectionProperties();
        selections = new SelectionService(
                new TaxonomyRepository(jdbc),
                new CandidateSearchRepository(named),
                new RankSignalRepository(jdbc),
                new SelectionResultRepository(named),
                new RankingService(properties),
                properties);
        seedTaxonomy();
        seedCandidates();
    }

    @Test
    void createReturnsSelectionWithBasisAndPositionExplanationPerCandidate() {
        SelectionRequest request = new SelectionRequest(
                "Backend middle", "Need backend middle", "backend", "middle",
                List.of("Java"), null);

        // POST /selections equivalent.
        SelectionResponse created = selections.create(request);

        // 1. Shape: id assigned, items non-empty, positions 1-based sequential.
        assertThat(created.id()).isPositive();
        assertThat(created.items()).isNotEmpty();
        assertThat(created.items().stream().map(SelectionItemResponse::position).toList())
                .containsExactlyElementsOf(sequentialPositions(created.items().size()));

        // 2. Exit criterion 3: EVERY item carries inclusion basis + position
        // explanation — all four blocks non-null and non-blank.
        for (SelectionItemResponse item : created.items()) {
            assertThat(item.explanation()).as("explanation of candidate %s", item.candidateId()).isNotNull();
            assertThat(item.explanation().categoryMatch())
                    .as("category_match of candidate %s", item.candidateId()).isNotBlank();
            assertThat(item.explanation().confirmedTestBasis())
                    .as("confirmed_test_basis of candidate %s", item.candidateId()).isNotBlank();
            assertThat(item.explanation().fspAchievements())
                    .as("fsp_achievements of candidate %s", item.candidateId()).isNotBlank();
            assertThat(item.explanation().stackMatch())
                    .as("stack_match of candidate %s", item.candidateId()).isNotBlank();
        }
        // At least one item shows the requested-stack overlap (matching stack).
        assertThat(created.items().stream()
                .map(item -> item.explanation().stackMatch())
                .collect(Collectors.joining(" | "))).contains("Java");

        // 3. Confirmed-first invariant through the full service path: the
        // unconfirmed candidate (cards.grade 'senior' != categories.grade
        // 'middle', despite the highest theta) is ranked last.
        List<Long> ordered = created.items().stream()
                .map(SelectionItemResponse::candidateId).toList();
        assertThat(ordered).contains(104L);
        assertThat(ordered.lastIndexOf(104L)).isEqualTo(ordered.size() - 1);

        // 4. Theta never disclosed: serialized JSON carries no theta/score fields,
        // explanations stay qualitative.
        String json = JSON.writeValueAsString(created).toLowerCase();
        assertThat(json).doesNotContain("theta");
        assertThat(json).doesNotContain("score");
        for (SelectionItemResponse item : created.items()) {
            assertThat(item.explanation().categoryMatch()).doesNotContainIgnoringCase("theta");
            assertThat(item.explanation().confirmedTestBasis()).doesNotContainIgnoringCase("theta");
            assertThat(item.explanation().fspAchievements()).doesNotContainIgnoringCase("theta");
            assertThat(item.explanation().stackMatch()).doesNotContainIgnoringCase("theta");
        }

        // 5. Snapshot persisted: selection_results row exists with
        // parent_selection_id NULL; GET-path equivalent returns the snapshot
        // equal to the create() response (positions + explanations identical).
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT parent_selection_id FROM selection_results WHERE id = ?", created.id());
        assertThat(row.get("parent_selection_id")).isNull();
        SelectionResponse fetched = selections.getById(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.parentSelectionId()).isNull();
        assertThat(fetched.items()).isEqualTo(created.items());
    }

    @Test
    void createWithUnknownTaxonomyFailsValidation() {
        assertThatThrownBy(() -> selections.create(new SelectionRequest(
                        "Title", null, "unknown-spec", "middle", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown specialization_code");
        assertThatThrownBy(() -> selections.create(new SelectionRequest(
                        "Title", null, "backend", "unknown-grade", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown grade");
    }

    private void seedTaxonomy() {
        jdbc.update("INSERT INTO taxonomy (kind, code, name, grade_order, is_active) VALUES "
                + "('specialization', 'backend', 'Backend', NULL, true), "
                + "('grade', 'junior', 'Junior', 0, true), "
                + "('grade', 'middle', 'Middle', 1, true), "
                + "('grade', 'senior', 'Senior', 2, true)");
    }

    private void seedCandidates() {
        // Categories: all in backend-middle; theta varies incl. NULL.
        jdbc.update("INSERT INTO candidate_categories"
                + " (candidate_id, specialization_code, grade, category_code, theta,"
                + " source_event_id, assigned_at) VALUES "
                + "(101, 'backend', 'middle', 'backend-middle', 1.5, 'evt-cat-101', now()), "
                + "(102, 'backend', 'middle', 'backend-middle', NULL, 'evt-cat-102', now()), "
                + "(103, 'backend', 'middle', 'backend-middle', 0.2, 'evt-cat-103', now()), "
                + "(104, 'backend', 'middle', 'backend-middle', 2.5, 'evt-cat-104', now())");
        // Cards: 104 declares 'senior' != assigned 'middle' -> unconfirmed;
        // stacks overlap the requested 'Java'; fsp counts vary incl. zero.
        jdbc.update("INSERT INTO candidate_cards"
                + " (candidate_id, display_name, specialization_code, grade, stack,"
                + " fsp_verified, fsp_achievements_count) VALUES "
                + "(101, 'Ivan P.', 'backend', 'middle', ARRAY['Java','PostgreSQL'], true, 3), "
                + "(102, 'Anna K.', 'backend', 'middle', ARRAY['Java'], false, 0), "
                + "(103, 'Oleg M.', 'backend', 'middle', ARRAY['Java','Kafka'], true, 5), "
                + "(104, 'Sara L.', 'backend', 'senior', ARRAY['Java','Go'], true, 2)");
        // Rank signals: weighted fsp achievements vary; 102 has NULL theta.
        jdbc.update("INSERT INTO rank_signals"
                + " (candidate_id, category_code, theta, fsp_verified,"
                + " fsp_achievements_count, fsp_achievements_weighted) VALUES "
                + "(101, 'backend-middle', 1.5, true, 3, 2.0), "
                + "(102, 'backend-middle', NULL, false, 0, 0.0), "
                + "(103, 'backend-middle', 0.2, true, 5, 3.0), "
                + "(104, 'backend-middle', 2.5, true, 2, 1.0)");
    }

    private static List<Integer> sequentialPositions(int size) {
        return java.util.stream.IntStream.rangeClosed(1, size).boxed().toList();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
