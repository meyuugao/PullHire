package me.yuugao.pullhire.assessment.repo;

import java.time.Instant;

import me.yuugao.pullhire.assessment.domain.Cooldown;
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
 * DB-backed checks for {@link CooldownRepository} against the {@code assessment}
 * schema ({@code docs/data_model.md §6.1}, {@code docs/testing_model.md §7.3}): a
 * first upsert inserts the candidate's cooldown and a second upsert updates it in
 * place on the unique key {@code (candidate_id, scope)} without creating a
 * duplicate; {@code findActive} reads {@code available_after}.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code ASSESSMENT_TEST_DB_URL} (compose/pg
 * instance); otherwise the class is skipped, so CI without a live database stays
 * green. No extra dependency is introduced.
 */
class CooldownPostgresDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private CooldownRepository cooldowns;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("ASSESSMENT_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "ASSESSMENT_TEST_DB_URL not set: DB-backed cooldown checks skipped");
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
        jdbc.execute("TRUNCATE cooldowns, grade_history, test_sessions RESTART IDENTITY CASCADE");
        cooldowns = new CooldownRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void upsertInsertsThenUpdatesInPlaceOnTheUniqueKey() {
        long candidate = 42L;
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-03-01T00:00:00Z");

        Cooldown inserted = cooldowns.upsert(candidate, first);
        assertThat(inserted.candidateId()).isEqualTo(candidate);
        assertThat(inserted.scope()).isEqualTo(CooldownRepository.SCOPE_GRADE_CHANGE);
        assertThat(inserted.availableAfter()).isEqualTo(first);
        assertThat(inserted.id()).isPositive();

        // Second upsert on the same (candidate_id, scope) updates in place: no duplicate.
        Cooldown updated = cooldowns.upsert(candidate, second);
        assertThat(updated.id()).isEqualTo(inserted.id());
        assertThat(updated.availableAfter()).isEqualTo(second);

        assertThat(countRows(candidate)).isEqualTo(1);

        assertThat(cooldowns.findActive(candidate, CooldownRepository.SCOPE_GRADE_CHANGE))
                .get()
                .extracting(Cooldown::availableAfter)
                .isEqualTo(second);
    }

    @Test
    void findActiveReturnsEmptyForUnknownCandidateAndUsesDefaultScope() {
        cooldowns.upsert(7L, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(cooldowns.findActive(99L)).isEmpty();
        assertThat(cooldowns.findActive(7L))
                .get()
                .extracting(Cooldown::scope)
                .isEqualTo(CooldownRepository.SCOPE_GRADE_CHANGE);
    }

    private long countRows(long candidateId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM cooldowns WHERE candidate_id = ?", Long.class, candidateId);
        return count == null ? -1L : count;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
