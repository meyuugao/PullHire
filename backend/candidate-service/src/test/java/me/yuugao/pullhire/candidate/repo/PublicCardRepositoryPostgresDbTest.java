package me.yuugao.pullhire.candidate.repo;

import java.time.Instant;

import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.domain.PublicCard;
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
 * DB-drift checks for {@link PublicCardRepository} against live PostgreSQL
 * (no Spring context, no Testcontainers). Runs only when a URL is provided
 * via {@code -Dpullhire.test.db.url} or {@code CANDIDATE_TEST_DB_URL}.
 */
class PublicCardRepositoryPostgresDbTest {

    private static String url;

    private ProfileRepository profiles;
    private PublicCardRepository cards;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("CANDIDATE_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "CANDIDATE_TEST_DB_URL not set: DB-backed card checks skipped");
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
        cards = new PublicCardRepository(named);
    }

    @Test
    void upsertInsertThenUpdate() {
        long profileId = insertProfile(21L);

        long id = cards.upsert(card(profileId, "Ivan P.", false));
        assertThat(id).isPositive();
        assertThat(cards.findByProfileId(profileId).orElseThrow().displayName()).isEqualTo("Ivan P.");

        long second = cards.upsert(card(profileId, "Ivan Updated", false));
        assertThat(second).isEqualTo(id);
        assertThat(cards.findByProfileId(profileId).orElseThrow().displayName()).isEqualTo("Ivan Updated");
    }

    @Test
    void deleteByProfileIdRemovesRow() {
        long profileId = insertProfile(22L);
        cards.upsert(card(profileId, "Ivan P.", false));
        assertThat(cards.findByProfileId(profileId)).isPresent();

        cards.deleteByProfileId(profileId);

        assertThat(cards.findByProfileId(profileId)).isEmpty();
    }

    @Test
    void markContactReleasedFlipsOnceThenIdempotent() {
        long profileId = insertProfile(23L);
        cards.upsert(card(profileId, "Ivan P.", false));

        assertThat(cards.markContactReleased(profileId)).isEqualTo(1);
        assertThat(cards.findByProfileId(profileId).orElseThrow().contactReleased()).isTrue();

        assertThat(cards.markContactReleased(profileId)).isZero();
    }

    private long insertProfile(long userId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return profiles.upsertByUserId(new Profile(0L, userId, "Ivan Petrov", "c@example.com", null,
                "Backend", "middle", null, new String[]{"Java"}, null, null, null, "backend", "it",
                "published", now, now));
    }

    private static PublicCard card(long profileId, String displayName, boolean released) {
        return new PublicCard(0L, profileId, displayName, "Backend dev", "it", "backend", "middle",
                new String[]{"Java"}, false, released, null);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
