package me.yuugao.pullhire.selection;

import java.sql.Array;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.ingest.AchievementsUpdatedEvent;
import me.yuugao.pullhire.selection.ingest.AchievementsUpdatedListener;
import me.yuugao.pullhire.selection.ingest.CardUpdatedEvent;
import me.yuugao.pullhire.selection.ingest.CardUpdatedListener;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedEvent;
import me.yuugao.pullhire.selection.ingest.CategoryAssignedListener;
import me.yuugao.pullhire.selection.repo.CandidateCardRepository;
import me.yuugao.pullhire.selection.repo.CandidateCategoryRepository;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import me.yuugao.pullhire.selection.repo.RankSignalRepository;
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
 * Convergence of the search projection via events (exit criterion 1, TD-1):
 * {@code assessment.category_assigned} (incl. republish on a new attempt) +
 * {@code candidate.card_updated} + {@code fsp.achievements_updated} converge
 * {@code candidate_cards} / {@code candidate_categories} / {@code rank_signals}
 * to last-writer-wins final state, duplicate {@code event_id} deliveries are
 * no-ops via {@code selection.processed_events}, and {@code candidate_cards}
 * carries no PII.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code SELECTION_TEST_DB_URL}; otherwise
 * the class is skipped. Listeners are invoked directly (no RabbitMQ).
 */
class CardConvergenceFlowTest {

    private static String url;

    private JdbcTemplate jdbc;
    private CategoryAssignedListener categories;
    private CardUpdatedListener cards;
    private AchievementsUpdatedListener achievements;

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
                + "processed_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        EventIdDedup dedup = new EventIdDedup(jdbc);
        ProcessedEventRepository processedEvents = new ProcessedEventRepository(jdbc);
        categories = new CategoryAssignedListener(dedup, processedEvents,
                new CandidateCategoryRepository(jdbc));
        cards = new CardUpdatedListener(dedup, processedEvents,
                new CandidateCardRepository(named));
        achievements = new AchievementsUpdatedListener(dedup, processedEvents,
                new RankSignalRepository(jdbc));
    }

    @Test
    void candidateCardsConvergeViaEventsWithRepublishAndWithoutPii() {
        // 1. category_assigned -> candidate_categories row appears.
        categories.onCategoryAssigned(categoryAssigned("evt-cat-1", "42", "backend", "middle",
                "backend-middle", "sess-1", "2026-10-01T00:00:00Z"));
        assertThat(categoryRow("42")).containsEntry("category_code", "backend-middle");

        // 1b. card_updated -> candidate_cards row matches consented fields only.
        cards.onCardUpdated(cardUpdated("evt-card-1", "42", true, "Ivan P.",
                "Backend developer", "backend", "middle", List.of("Java", "PostgreSQL"), false, false));
        Map<String, Object> card = cardRow("42");
        assertThat(card.get("display_name")).isEqualTo("Ivan P.");
        assertThat(card.get("specialization_code")).isEqualTo("backend");
        assertThat(card.get("grade")).isEqualTo("middle");

        // 1c. Duplicate delivery of the SAME card_updated event_id -> no change, no duplicates.
        long cardEventsBefore = processedCount();
        Map<String, Object> cardBefore = cardRow("42");
        cards.onCardUpdated(cardUpdated("evt-card-1", "42", true, "Ivan P.",
                "Backend developer", "backend", "middle", List.of("Java", "PostgreSQL"), false, false));
        assertThat(comparableRow(cardRow("42"))).isEqualTo(comparableRow(cardBefore));
        assertThat(stackOf(cardRow("42"))).isEqualTo(stackOf(cardBefore));
        assertThat(cardCount("42")).isEqualTo(1L);
        assertThat(processedCount()).isEqualTo(cardEventsBefore);

        // 2. TD-1 republish: new attempt, changed category, new event_id overwrites.
        categories.onCategoryAssigned(categoryAssigned("evt-cat-2", "42", "backend", "senior",
                "backend-senior", "sess-2", "2026-10-02T00:00:00Z"));
        assertThat(categoryRow("42")).containsEntry("category_code", "backend-senior");
        assertThat(categoryRow("42")).containsEntry("source_event_id", "evt-cat-2");
        long eventsBefore = processedCount();

        // 2b. Duplicate delivery of the SAME event_id -> no change, no duplicates.
        categories.onCategoryAssigned(categoryAssigned("evt-cat-2", "42", "backend", "senior",
                "backend-senior", "sess-2", "2026-10-02T00:00:00Z"));
        assertThat(categoryRow("42")).containsEntry("category_code", "backend-senior");
        assertThat(categoryCount("42")).isEqualTo(1L);
        assertThat(processedCount()).isEqualTo(eventsBefore);

        // 3. fsp.achievements_updated -> rank_signals updated, theta preserved; re-delivery idempotent.
        Map<String, Object> rankBefore = rankRow("42", "backend-senior");
        assertThat(rankBefore).isNull();
        achievements.onAchievementsUpdated(achievementsUpdated("evt-fsp-1", "42", "present",
                List.of(place("a-1", 1), place("a-2", 3), place("a-3", null))));
        Map<String, Object> rank = rankRow("42", "backend-senior");
        assertThat(rank.get("fsp_verified")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) rank.get("fsp_achievements_count")).intValue()).isEqualTo(3);
        assertThat(((Number) rank.get("fsp_achievements_weighted")).doubleValue()).isEqualTo(1.75);
        assertThat(rank.get("theta")).isNull();
        // Rank write must not disturb the category projection (theta preserved as NULL here).
        assertThat(categoryRow("42")).containsEntry("category_code", "backend-senior");
        long rankEventsBefore = processedCount();
        achievements.onAchievementsUpdated(achievementsUpdated("evt-fsp-1", "42", "present",
                List.of(place("a-1", 1), place("a-2", 3), place("a-3", null))));
        assertThat(rankRow("42", "backend-senior").get("fsp_achievements_count"))
                .isEqualTo(rank.get("fsp_achievements_count"));
        assertThat(processedCount()).isEqualTo(rankEventsBefore);

        // 4. Convergence final state: last-writer-wins per projection.
        assertThat(categoryRow("42")).containsEntry("category_code", "backend-senior");
        assertThat(categoryRow("42")).containsEntry("grade", "senior");
        assertThat(cardRow("42").get("display_name")).isEqualTo("Ivan P.");
        assertThat(cardRow("42").get("fsp_verified")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) cardRow("42").get("fsp_achievements_count")).intValue()).isEqualTo(3);
        assertThat(((Number) rankRow("42", "backend-senior").get("fsp_achievements_weighted"))
                .doubleValue()).isEqualTo(1.75);

        // 5. No-PII assertion on candidate_cards.
        assertNoPiiInCandidateCards();
    }

    private void assertNoPiiInCandidateCards() {
        List<String> piiBlacklist = List.of("email", "phone", "contact", "first_name", "last_name", "full_name");
        jdbc.query("SELECT * FROM candidate_cards WHERE candidate_id = 42", rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String column = meta.getColumnName(i).toLowerCase();
                assertThat(piiBlacklist).noneMatch(column::contains);
            }
            while (rs.next()) {
                String displayName = rs.getString("display_name");
                assertThat(displayName).doesNotContain("@");
                assertThat(displayName).doesNotContain("+7");
            }
            return null;
        });
    }

    private Map<String, Object> categoryRow(String candidateId) {
        return jdbc.queryForMap("SELECT * FROM candidate_categories WHERE candidate_id = ?",
                Long.parseLong(candidateId));
    }

    private Map<String, Object> cardRow(String candidateId) {
        return jdbc.queryForMap("SELECT * FROM candidate_cards WHERE candidate_id = ?",
                Long.parseLong(candidateId));
    }

    private static Map<String, Object> comparableRow(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        for (Map.Entry<String, Object> entry : copy.entrySet()) {
            if (entry.getValue() instanceof Array array) {
                try {
                    Object arr = array.getArray();
                    copy.put(entry.getKey(),
                            arr instanceof Object[] objects ? Arrays.asList(objects) : arr);
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return copy;
    }

    private static List<Object> stackOf(Map<String, Object> row) {
        Object stack = row.get("stack");
        if (stack instanceof Array array) {
            try {
                Object arr = array.getArray();
                return arr instanceof Object[] objects ? Arrays.asList(objects) : List.of(arr);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        if (stack instanceof List<?> list) {
            return List.copyOf(list);
        }
        return stack == null ? null : List.of(stack);
    }

    private Map<String, Object> rankRow(String candidateId, String categoryCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM rank_signals WHERE candidate_id = ? AND category_code = ?",
                Long.parseLong(candidateId), categoryCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long categoryCount(String candidateId) {
        return jdbc.queryForObject("SELECT count(*) FROM candidate_categories WHERE candidate_id = ?",
                Long.class, Long.parseLong(candidateId));
    }

    private Long cardCount(String candidateId) {
        return jdbc.queryForObject("SELECT count(*) FROM candidate_cards WHERE candidate_id = ?",
                Long.class, Long.parseLong(candidateId));
    }

    private long processedCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM processed_events", Long.class);
        return count == null ? 0L : count;
    }

    private static CategoryAssignedEvent categoryAssigned(String eventId, String candidateId,
            String spec, String grade, String categoryCode, String sessionId, String assignedAt) {
        return new CategoryAssignedEvent(eventId, CategoryAssignedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "assessment-service", null,
                new CategoryAssignedEvent.Payload(candidateId, spec, grade, categoryCode,
                        sessionId, assignedAt));
    }

    private static CardUpdatedEvent cardUpdated(String eventId, String candidateId, boolean visible,
            String displayName, String headline, String spec, String grade, List<String> stack,
            boolean fspVerified, boolean contactReleased) {
        return new CardUpdatedEvent(eventId, CardUpdatedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "candidate-service", null,
                new CardUpdatedEvent.Payload(candidateId, visible, displayName, headline, null,
                        spec, grade, stack, fspVerified, contactReleased));
    }

    private static AchievementsUpdatedEvent achievementsUpdated(String eventId, String candidateId,
            String historyStatus, List<AchievementsUpdatedEvent.Achievement> achievements) {
        return new AchievementsUpdatedEvent(eventId, AchievementsUpdatedEvent.TYPE, 1,
                "2026-10-01T00:00:00Z", "fsp-service", null,
                new AchievementsUpdatedEvent.Payload(candidateId, "fsp-1", historyStatus,
                        achievements));
    }

    private static AchievementsUpdatedEvent.Achievement place(String id, Integer place) {
        return new AchievementsUpdatedEvent.Achievement(id, "title-" + id, "contest",
                "2026-09-01T00:00:00Z", place);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
