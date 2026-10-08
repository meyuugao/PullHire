package me.yuugao.pullhire.assessment.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-backed checks for {@link TestSessionRepository} and {@link AttemptRepository}
 * against the {@code assessment} schema (docs/data_model.md §6.1): session create
 * / load / update round-trips, ordered attempt serving, the
 * {@code UNIQUE (session_id, item_order)} and
 * {@code UNIQUE (session_id, item_id)} rejections, and the prior-served-variant
 * ledger join preventing a variant from being re-served
 * (docs/testing_model.md §3.4).
 *
 * <p>Runs only when a PostgreSQL URL is provided via {@code -Dpullhire.test.db.url}
 * or {@code ASSESSMENT_TEST_DB_URL} (compose/pg instance); otherwise the class is
 * skipped, so CI without a live database stays green. No extra dependency is
 * introduced.
 */
class SessionAttemptPostgresDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private TestSessionRepository sessions;
    private AttemptRepository attempts;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("ASSESSMENT_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "ASSESSMENT_TEST_DB_URL not set: DB-backed session/attempt checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("assessment")
                .defaultSchema("assessment")
                .locations("classpath:db/migration/assessment")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE test_sessions, attempts, item_bank RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        sessions = new TestSessionRepository(named);
        attempts = new AttemptRepository(named);
    }

    @Test
    void createAndLoadSessionRoundTripPreservesColumns() {
        TestSession session = newSession(0L, 42L);
        long id = sessions.create(session);

        assertThat(id).isPositive();
        TestSession loaded = sessions.findById(id).orElseThrow();
        assertThat(loaded.candidateId()).isEqualTo(42L);
        assertThat(loaded.industryCode()).isEqualTo("industry-1");
        assertThat(loaded.specializationCode()).isEqualTo("spec-1");
        assertThat(loaded.targetGrade()).isEqualTo("middle");
        assertThat(loaded.surveyAnswers()).isEqualTo("{\"q\":1}");
        assertThat(loaded.declaredDimensions()).containsExactly("seniority", "industry");
        assertThat(loaded.formSeed()).isEqualTo(777L);
        assertThat(loaded.status()).isEqualTo(SessionStatus.STARTED);
        assertThat(loaded.itemCount()).isZero();
        assertThat(loaded.startedAt()).isNotNull();
        assertThat(loaded.completedAt()).isNull();
    }

    @Test
    void updateWritesBackStatusThetaAndCompletion() {
        long id = sessions.create(newSession(0L, 42L));

        int changed = sessions.update(new TestSession(id, 42L, "industry-1", "spec-1", "middle", null, null,
                777L, SessionStatus.COMPLETED, new BigDecimal("0.125"), new BigDecimal("0.300"), 12,
                Instant.now(), Instant.parse("2026-02-01T10:30:00Z")));

        assertThat(changed).isEqualTo(1);
        TestSession loaded = sessions.findById(id).orElseThrow();
        assertThat(loaded.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(loaded.theta()).isEqualByComparingTo("0.125");
        assertThat(loaded.thetaSe()).isEqualByComparingTo("0.300");
        assertThat(loaded.itemCount()).isEqualTo(12);
        assertThat(loaded.completedAt()).isEqualTo(Instant.parse("2026-02-01T10:30:00Z"));
    }

    @Test
    void findByCandidateReturnsSessionsNewestFirst() {
        long first = sessions.create(newSession(0L, 42L));
        jdbc.update("UPDATE test_sessions SET started_at = '2026-01-01T00:00:00Z' WHERE id = ?", first);
        long second = sessions.create(newSession(0L, 42L));
        jdbc.update("UPDATE test_sessions SET started_at = '2026-03-01T00:00:00Z' WHERE id = ?", second);
        sessions.create(newSession(0L, 999L));

        assertThat(sessions.findByCandidate(42L)).extracting(TestSession::id).containsExactly(second, first);
    }

    @Test
    void servesOrderedAttemptsAndRecordsAnswer() {
        long sessionId = sessions.create(newSession(0L, 42L));
        long itemOne = item("fam-A");
        long itemTwo = item("fam-A");

        long first = attempts.insert(new Attempt(0L, sessionId, itemOne, 1, 11L, null, null, null, null, Instant.now()));
        long second = attempts.insert(new Attempt(0L, sessionId, itemTwo, 2, 12L, null, null, null, null, Instant.now()));

        assertThat(attempts.saveAnswer(second, "{\"choice\":\"b\"}", true, 4200, "[{\"type\":\"blur\"}]"))
                .isEqualTo(1);

        List<Attempt> served = attempts.findBySession(sessionId);
        assertThat(served).extracting(Attempt::id).containsExactly(first, second);
        assertThat(served).extracting(Attempt::itemOrder).containsExactly(1, 2);
        assertThat(served.get(0).isCorrect()).isNull();
        assertThat(served.get(1).isCorrect()).isTrue();
        assertThat(served.get(1).answer()).isEqualTo("{\"choice\":\"b\"}");
        assertThat(served.get(1).responseMs()).isEqualTo(4200);
        assertThat(served.get(1).proctoringFlags()).isEqualTo("[{\"type\":\"blur\"}]");
    }

    @Test
    void duplicateItemOrderRejectedByUniqueConstraint() {
        long sessionId = sessions.create(newSession(0L, 42L));
        long itemOne = item("fam-A");
        long itemTwo = item("fam-A");
        attempts.insert(new Attempt(0L, sessionId, itemOne, 1, 11L, null, null, null, null, Instant.now()));

        assertThatThrownBy(() -> attempts.insert(
                new Attempt(0L, sessionId, itemTwo, 1, 12L, null, null, null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempts_session_item_order");
    }

    @Test
    void duplicateItemRejectedByUniqueConstraint() {
        long sessionId = sessions.create(newSession(0L, 42L));
        long itemOne = item("fam-A");
        attempts.insert(new Attempt(0L, sessionId, itemOne, 1, 11L, null, null, null, null, Instant.now()));

        assertThatThrownBy(() -> attempts.insert(
                new Attempt(0L, sessionId, itemOne, 2, 12L, null, null, null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempts_session_item");
    }

    @Test
    void priorServedVariantsReturnsServedSeedsAndIsScopedToCandidateAndFamily() {
        long itemA1 = item("fam-A");
        long itemA2 = item("fam-A");
        long itemB1 = item("fam-B");

        long sessionOne = sessions.create(newSession(0L, 42L));
        attempts.insert(new Attempt(0L, sessionOne, itemA1, 1, 11L, null, null, null, null, Instant.now()));
        attempts.insert(new Attempt(0L, sessionOne, itemA2, 2, 12L, null, null, null, null, Instant.now()));
        attempts.insert(new Attempt(0L, sessionOne, itemB1, 3, 99L, null, null, null, null, Instant.now()));

        long otherCandidate = sessions.create(newSession(0L, 43L));
        long otherItem = item("fam-A");
        attempts.insert(new Attempt(0L, otherCandidate, otherItem, 1, 55L, null, null, null, null, Instant.now()));

        assertThat(attempts.priorServedVariants(42L, "fam-A")).containsExactly(11L, 12L);
        assertThat(attempts.priorServedVariants(42L, "fam-B")).containsExactly(99L);
        assertThat(attempts.priorServedVariants(43L, "fam-A")).containsExactly(55L);
        assertThat(attempts.priorServedVariants(42L, "fam-unknown")).isEmpty();
    }

    private long item(String templateFamily) {
        Long id = jdbc.queryForObject("""
                INSERT INTO item_bank (item_kind, template_family, concept_code, specialization_code,
                        difficulty, payload)
                VALUES ('template', ?, 'concept-1', 'spec-1', 0.500, CAST('{"q":1}' AS jsonb))
                RETURNING id
                """, Long.class, templateFamily);
        return id == null ? -1L : id;
    }

    private static TestSession newSession(long id, long candidateId) {
        return new TestSession(id, candidateId, "industry-1", "spec-1", "middle", "{\"q\":1}",
                List.of("seniority", "industry"), 777L, SessionStatus.STARTED,
                null, null, 0, Instant.now(), null);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
