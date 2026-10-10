package me.yuugao.pullhire.candidate.ingest;

import java.time.Instant;
import java.util.Map;

import me.yuugao.pullhire.candidate.application.CardProjectorService;
import me.yuugao.pullhire.candidate.application.EventPublisher;
import me.yuugao.pullhire.candidate.domain.PrivacySettings;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.repo.PrivacySettingsRepository;
import me.yuugao.pullhire.candidate.repo.ProcessedEventRepository;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import me.yuugao.pullhire.candidate.repo.PublicCardRepository;
import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
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

/**
 * DB-drift checks for the contact-release path against live PostgreSQL
 * (no Spring context, no Testcontainers): {@link ProcessedEventRepository}
 * claim semantics plus the full {@link InvitationAcceptedListener} path —
 * release flips the flag, writes one {@code candidate.contact_released}
 * outbox row, and a duplicate redelivery writes nothing more.
 *
 * <p>Runs only when a URL is provided via {@code -Dpullhire.test.db.url} or
 * {@code CANDIDATE_TEST_DB_URL}.
 */
class InvitationAcceptancePostgresDbTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private JdbcTemplate jdbc;
    private ProcessedEventRepository processedEvents;
    private InvitationAcceptedListener listener;
    private PublicCardRepository publicCards;
    private ProfileRepository profiles;
    private PrivacySettingsRepository privacy;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("CANDIDATE_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "CANDIDATE_TEST_DB_URL not set: DB-backed ingest checks skipped");
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
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE profiles, resumes, privacy_settings, public_cards, outbox_events, "
                + "processed_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        profiles = new ProfileRepository(named);
        privacy = new PrivacySettingsRepository(named);
        publicCards = new PublicCardRepository(named);
        processedEvents = new ProcessedEventRepository(jdbc);
        EventPublisher events = new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc)));
        CardProjectorService projector = new CardProjectorService(profiles, privacy, publicCards, events);
        listener = new InvitationAcceptedListener(new EventIdDedup(jdbc), processedEvents, privacy,
                projector, publicCards, profiles, events);
    }

    @Test
    void claimIsTrueOnceThenFalse() {
        assertThat(processedEvents.claim("evt-claim-1")).isTrue();
        assertThat(processedEvents.claim("evt-claim-1")).isFalse();
    }

    @Test
    void acceptedReleaseWritesOneOutboxRowAndDuplicateIsNoOp() {
        long profileId = insertProfile(51L);
        privacy.upsert(new PrivacySettings(profileId, true, true, true, null));
        String candidateId = Long.toString(profileId);

        listener.onInvitationAccepted(event("evt-accept-1", candidateId));

        assertThat(publicCards.findByProfileId(profileId).orElseThrow().contactReleased()).isTrue();
        assertThat(contactReleasedCount(candidateId)).isEqualTo(1L);
        Map<String, Object> row = contactReleasedRow(candidateId);
        assertThat(row.get("event_type")).isEqualTo(EventNames.CANDIDATE_CONTACT_RELEASED);
        assertThat(row.get("producer")).isEqualTo("candidate-service");
        assertThat(payload(row)).containsEntry("candidate_id", candidateId);

        // Duplicate redelivery of the same event_id must not emit a second row.
        listener.onInvitationAccepted(event("evt-accept-1", candidateId));

        assertThat(contactReleasedCount(candidateId)).isEqualTo(1L);
    }

    private long insertProfile(long userId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return profiles.upsertByUserId(new Profile(0L, userId, "Ivan Petrov", "candidate@example.com",
                "+7 999 123-45-67", "Backend", "middle", null, new String[]{"Java"}, null, null,
                null, "backend", "it", "published", now, now));
    }

    private static InvitationAcceptedEvent event(String eventId, String candidateId) {
        return new InvitationAcceptedEvent(eventId, InvitationAcceptedEvent.TYPE, 1,
                "2026-10-07T10:00:00Z", "outreach-service", null,
                new InvitationAcceptedEvent.Payload("inv-1", candidateId, "emp-1"));
    }

    private long contactReleasedCount(String candidateId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = ? AND payload->>'candidate_id' = ?",
                Long.class, EventNames.CANDIDATE_CONTACT_RELEASED, candidateId);
        return count == null ? 0L : count;
    }

    private Map<String, Object> contactReleasedRow(String candidateId) {
        return jdbc.queryForMap(
                "SELECT event_type, producer, payload::text AS payload FROM outbox_events"
                        + " WHERE event_type = ? AND payload->>'candidate_id' = ?",
                EventNames.CANDIDATE_CONTACT_RELEASED, candidateId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> row) {
        return JSON.readValue(String.valueOf(row.get("payload")), Map.class);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
