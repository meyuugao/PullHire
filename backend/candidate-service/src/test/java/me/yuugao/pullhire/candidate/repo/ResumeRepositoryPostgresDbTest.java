package me.yuugao.pullhire.candidate.repo;

import java.time.Instant;

import me.yuugao.pullhire.candidate.domain.ParseStatus;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.domain.Resume;
import me.yuugao.pullhire.candidate.domain.ResumeKind;
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
 * DB-drift checks for {@link ResumeRepository} against live PostgreSQL
 * (no Spring context, no Testcontainers). Runs only when a URL is provided
 * via {@code -Dpullhire.test.db.url} or {@code CANDIDATE_TEST_DB_URL}.
 */
class ResumeRepositoryPostgresDbTest {

    private static String url;

    private ProfileRepository profiles;
    private ResumeRepository resumes;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("CANDIDATE_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "CANDIDATE_TEST_DB_URL not set: DB-backed resume checks skipped");
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
        resumes = new ResumeRepository(named);
    }

    @Test
    void insertReturnsIdAndLatestWins() {
        long profileId = insertProfile(31L);

        long first = resumes.insert(resume(profileId, ResumeKind.UPLOADED, "a.pdf"));
        long second = resumes.insert(resume(profileId, ResumeKind.UPLOADED, "b.pdf"));
        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);

        Resume latest = resumes.findLatestByProfileIdAndKind(profileId, ResumeKind.UPLOADED).orElseThrow();
        assertThat(latest.id()).isEqualTo(second);
        assertThat(latest.fileName()).isEqualTo("b.pdf");
    }

    @Test
    void kindIsolation() {
        long profileId = insertProfile(32L);
        resumes.insert(resume(profileId, ResumeKind.UPLOADED, "up.pdf"));
        resumes.insert(resume(profileId, ResumeKind.GENERATED, "gen.pdf"));

        assertThat(resumes.findLatestByProfileIdAndKind(profileId, ResumeKind.UPLOADED).orElseThrow()
                .fileName()).isEqualTo("up.pdf");
        assertThat(resumes.findLatestByProfileIdAndKind(profileId, ResumeKind.GENERATED).orElseThrow()
                .fileName()).isEqualTo("gen.pdf");
        assertThat(resumes.findLatestByProfileIdAndKind(-7L, ResumeKind.UPLOADED)).isEmpty();
    }

    private long insertProfile(long userId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return profiles.upsertByUserId(new Profile(0L, userId, "Ivan Petrov", "c@example.com", null,
                null, null, null, null, null, null, null, null, null, "draft", now, now));
    }

    private static Resume resume(long profileId, ResumeKind kind, String fileName) {
        return new Resume(0L, profileId, kind, fileName, "/files/" + fileName, "application/pdf",
                1024L, ParseStatus.PENDING, null, null);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
