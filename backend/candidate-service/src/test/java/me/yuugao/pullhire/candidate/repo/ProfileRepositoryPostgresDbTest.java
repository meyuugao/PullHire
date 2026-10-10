package me.yuugao.pullhire.candidate.repo;

import java.math.BigDecimal;
import java.time.Instant;

import me.yuugao.pullhire.candidate.domain.Profile;
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
 * DB-drift checks for {@link ProfileRepository} against live PostgreSQL
 * (no Spring context, no Testcontainers). Runs only when a URL is provided
 * via {@code -Dpullhire.test.db.url} or {@code CANDIDATE_TEST_DB_URL}.
 */
class ProfileRepositoryPostgresDbTest {

    private static String url;

    private ProfileRepository profiles;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("CANDIDATE_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "CANDIDATE_TEST_DB_URL not set: DB-backed profile checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("candidate")
                .defaultSchema("candidate")
                .locations("classpath:db/migration/candidate")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE profiles, resumes, privacy_settings, public_cards, outbox_events, "
                + "processed_events RESTART IDENTITY CASCADE");
        profiles = new ProfileRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void upsertByUserIdInsertThenUpdateKeepsIdAndDefaultsDraft() {
        long id = profiles.upsertByUserId(profile(0L, 11L, "Ivan Petrov", null));
        assertThat(id).isPositive();

        Profile stored = profiles.findByUserId(11L).orElseThrow();
        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.fullName()).isEqualTo("Ivan Petrov");
        assertThat(stored.status()).isEqualTo("draft");

        long second = profiles.upsertByUserId(profile(0L, 11L, "Ivan Updated", "published"));
        assertThat(second).isEqualTo(id);
        assertThat(profiles.findByUserId(11L).orElseThrow().fullName()).isEqualTo("Ivan Updated");
        assertThat(profiles.findByUserId(11L).orElseThrow().status()).isEqualTo("published");
    }

    @Test
    void findByIdRoundTrip() {
        long id = profiles.upsertByUserId(profile(0L, 12L, "Anna K.", "published"));

        Profile found = profiles.findById(id).orElseThrow();
        assertThat(found.userId()).isEqualTo(12L);
        assertThat(found.fullName()).isEqualTo("Anna K.");
        assertThat(profiles.findById(-99L)).isEmpty();
    }

    @Test
    void upsertByIdConflictPathUpdatesRow() {
        profiles.upsertById(profile(500L, 13L, "First", "draft"));
        assertThat(profiles.findById(500L).orElseThrow().fullName()).isEqualTo("First");

        profiles.upsertById(profile(500L, 13L, "Second", "published"));
        Profile updated = profiles.findById(500L).orElseThrow();
        assertThat(updated.fullName()).isEqualTo("Second");
        assertThat(updated.status()).isEqualTo("published");
    }

    private static Profile profile(long id, long userId, String fullName, String status) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(id, userId, fullName, "candidate@example.com", "+7 999 000-00-00",
                "Backend", "middle", new BigDecimal("3.0"), new String[]{"Java"}, new String[]{"SQL"},
                new String[]{"team"}, "summary", "backend", "it", status, now, now);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
