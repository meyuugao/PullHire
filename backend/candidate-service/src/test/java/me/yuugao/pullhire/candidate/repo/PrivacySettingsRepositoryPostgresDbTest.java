package me.yuugao.pullhire.candidate.repo;

import java.time.Instant;

import me.yuugao.pullhire.candidate.domain.PrivacySettings;
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
 * DB-drift checks for {@link PrivacySettingsRepository} against live PostgreSQL
 * (no Spring context, no Testcontainers). Runs only when a URL is provided
 * via {@code -Dpullhire.test.db.url} or {@code CANDIDATE_TEST_DB_URL}.
 */
class PrivacySettingsRepositoryPostgresDbTest {

    private static String url;

    private ProfileRepository profiles;
    private PrivacySettingsRepository privacy;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("CANDIDATE_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "CANDIDATE_TEST_DB_URL not set: DB-backed privacy checks skipped");
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
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        profiles = new ProfileRepository(named);
        privacy = new PrivacySettingsRepository(named);
    }

    @Test
    void upsertInsertThenReplace() {
        long profileId = insertProfile(41L);
        assertThat(privacy.findByProfileId(profileId)).isEmpty();

        privacy.upsert(new PrivacySettings(profileId, true, true, false, null));
        PrivacySettings stored = privacy.findByProfileId(profileId).orElseThrow();
        assertThat(stored.visibleToEmployers()).isTrue();
        assertThat(stored.showContacts()).isFalse();

        privacy.upsert(new PrivacySettings(profileId, false, false, true, null));
        PrivacySettings replaced = privacy.findByProfileId(profileId).orElseThrow();
        assertThat(replaced.visibleToEmployers()).isFalse();
        assertThat(replaced.showFsp()).isFalse();
        assertThat(replaced.showContacts()).isTrue();
    }

    private long insertProfile(long userId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return profiles.upsertByUserId(new Profile(0L, userId, "Ivan Petrov", "c@example.com", null,
                null, null, null, null, null, null, null, null, null, "draft", now, now));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
