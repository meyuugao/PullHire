package me.yuugao.pullhire.outreach;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
import me.yuugao.pullhire.outreach.application.CategoryCheckClient;
import me.yuugao.pullhire.outreach.application.EventPublisher;
import me.yuugao.pullhire.outreach.application.InvitationService;
import me.yuugao.pullhire.outreach.application.InvitationService.CreateCommand;
import me.yuugao.pullhire.outreach.config.AppProperties;
import me.yuugao.pullhire.outreach.domain.Invitation;
import me.yuugao.pullhire.outreach.ingest.ContactReleasedEvent;
import me.yuugao.pullhire.outreach.ingest.ContactReleasedListener;
import me.yuugao.pullhire.outreach.repo.InvitationEventRepository;
import me.yuugao.pullhire.outreach.repo.InvitationRepository;
import me.yuugao.pullhire.outreach.repo.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ST-8.14 — accept→release chain (criterion 3): an accepted invitation gains
 * its {@code released_contact} snapshot plus one {@code contact_released} /
 * {@code system} audit row only after {@code candidate.contact_released}; a
 * replay of the same {@code event_id} is a no-op.
 *
 * <p>Runs only when {@code OUTREACH_TEST_DB_URL} (or
 * {@code -Dpullhire.test.db.url}) is set; otherwise the class is skipped.
 * Direct invocation — no Spring context, no RabbitMQ, no Testcontainers.
 *
 * <p>Scope note: outreach-service tests must not depend on candidate-service
 * code, so the candidate link is emulated by {@link CandidateSide}, which
 * mirrors the real guards of
 * {@code candidate.ingest.InvitationAcceptedListener} (privacy
 * {@code show_contacts}, contacts present) and emits the envelope with the
 * exact payload shape of candidate
 * {@code EventPublisher.publishContactReleased} ({@code candidate_id,
 * contact_email, contact_phone, released_at}). The outreach side under test —
 * {@link ContactReleasedListener} — is the real production code.
 *
 * <p>Payload parity: {@link #candidateSideEnvelopePayloadMatchesContract()}
 * pins the emulated candidate leg built by {@link CandidateSide} to the
 * committed {@code contracts/events/candidate.contact_released.schema.json}.
 * True cross-service end-to-end coverage is the S12 7-step smoke, not this
 * class.
 */
class ContactReleaseChainTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private InvitationService service;
    private ContactReleasedListener released;

    @BeforeAll
    static void setUpSchema() {
        url = OutreachTestDb.requireDb();
        OutreachTestDb.migrate(url);
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = OutreachTestDb.dataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        named = new NamedParameterJdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE invitations, invitation_events, outbox_events, processed_events "
                + "RESTART IDENTITY CASCADE");
        InvitationRepository invitations = new InvitationRepository(named);
        InvitationEventRepository events = new InvitationEventRepository(named);
        CategoryCheckClient categories = new CategoryCheckClient(new AppProperties()) {
            @Override
            public boolean hasCategory(long candidateId) {
                return true;
            }
        };
        service = new InvitationService(invitations, events, categories,
                new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc))));
        released = new ContactReleasedListener(new ProcessedEventRepository(jdbc), invitations, events);
    }

    @Test
    void acceptedInvitationGainsReleasedContactAfterContactReleased() {
        Invitation created = service.create(21L, createCommand(601L));
        service.accept(created.id(), 601L);

        // Accept discloses nothing by itself (criterion 3).
        Invitation afterAccept = invitations().findById(created.id()).orElseThrow();
        assertThat(afterAccept.releasedContact()).isNull();
        assertThat(afterAccept.contactReleasedAt()).isNull();

        // Candidate side releases (show_contacts=true, contacts present).
        ContactReleasedEvent event = CandidateSide.release("evt-release-1", "601",
                "candidate601@test.test", "+7-900-000-60-01", Instant.parse("2026-10-09T10:00:00Z"));
        released.onContactReleased(event);

        // GET-visible data changed: snapshot + timestamp filled via the service read path.
        Invitation visible = service.listForCandidate(601L, 0, 20).items().get(0);
        assertThat(visible.releasedContact()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = JSON.readValue(visible.releasedContact(), Map.class);
        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
                "contact_email", "candidate601@test.test",
                "contact_phone", "+7-900-000-60-01"));
        assertThat(visible.contactReleasedAt()).isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));

        // Exactly one audit row with the release type and system actor.
        assertThat(eventCount(created.id(), "contact_released")).isOne();
        assertThat(actorOf(created.id(), "contact_released")).isEqualTo("system");

        // Replay of the same event_id ⇒ no-op (no second audit row, snapshot untouched).
        released.onContactReleased(event);
        assertThat(eventCount(created.id(), "contact_released")).isOne();
        Invitation replayed = invitations().findById(created.id()).orElseThrow();
        assertThat(replayed.releasedContact()).isEqualTo(visible.releasedContact());
    }

    @Test
    void hiddenContactsMeanNoRelease() {
        Invitation created = service.create(21L, createCommand(602L));
        service.accept(created.id(), 602L);

        // show_contacts=false ⇒ candidate side emits nothing (OQ-22 residual),
        // so there is nothing to feed the consumer: the invitation stays
        // unreleased with lifecycle rows only.
        ContactReleasedEvent event = CandidateSide.releaseHidden("evt-release-hidden", "602");
        assertThat(event).isNull();

        Invitation visible = invitations().findById(created.id()).orElseThrow();
        assertThat(visible.releasedContact()).isNull();
        assertThat(visible.contactReleasedAt()).isNull();
        assertThat(eventCount(created.id(), "contact_released")).isZero();
        // Only the lifecycle rows exist.
        assertThat(eventCount(created.id(), "sent")).isOne();
        assertThat(eventCount(created.id(), "accepted")).isOne();
    }

    private InvitationRepository invitations() {
        return new InvitationRepository(named);
    }

    /**
     * Payload parity: the envelope emitted by the {@link CandidateSide} test
     * double carries the payload keys of the real candidate
     * {@code EventPublisher#publishContactReleased} and validates against the
     * committed {@code candidate.contact_released.schema.json}.
     */
    @Test
    void candidateSideEnvelopePayloadMatchesContract() throws Exception {
        ContactReleasedEvent event = CandidateSide.release("evt-parity-1", "601",
                "candidate601@test.test", "+7-900-000-60-01", Instant.parse("2026-10-09T10:00:00Z"));
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo(ContactReleasedEvent.TYPE);

        Map<String, Object> schema = readObject(contractsDir().resolve("candidate.contact_released.schema.json"));
        JsonSchema.validate("$", schema, payloadOf(event));
    }

    private long eventCount(long invitationId, String eventType) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_events WHERE invitation_id = ? AND event_type = ?",
                Long.class, invitationId, eventType);
        return count == null ? 0L : count;
    }

    private String actorOf(long invitationId, String eventType) {
        return jdbc.queryForObject(
                "SELECT actor_role FROM invitation_events WHERE invitation_id = ? AND event_type = ?",
                String.class, invitationId, eventType);
    }

    private static CreateCommand createCommand(long candidateId) {
        return new CreateCommand(candidateId, null, null, "Приглашаем в команду", "Acme",
                new BigDecimal("100000"), new BigDecimal("150000"), "gross",
                "hr@acme.test", null, null);
    }

    /** Payload of the emulated candidate envelope as a plain map (null-friendly). */
    private static Map<String, Object> payloadOf(ContactReleasedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", event.payload().candidateId());
        payload.put("contact_email", event.payload().contactEmail());
        payload.put("contact_phone", event.payload().contactPhone());
        payload.put("released_at", event.payload().releasedAt());
        return payload;
    }

    /** Resolves {@code <repo>/contracts/events} via the Gradle-provided system property, else by walking up. */
    private static Path contractsDir() {
        String property = System.getProperty("pullhire.contracts.dir");
        if (property != null && Files.isDirectory(Path.of(property))) {
            return Path.of(property);
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("contracts").resolve("events");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "contracts/events not found; run via Gradle or set -Dpullhire.contracts.dir");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readObject(Path path) throws Exception {
        return JSON.readValue(Files.readString(path), Map.class);
    }

    /**
     * Test double for the candidate link: mirrors the guards of the real
     * {@code InvitationAcceptedListener#onInvitationAccepted} —
     * {@code show_contacts} privacy flag and at least one contact present
     * (F13) — and builds the {@code candidate.contact_released} envelope with
     * the payload keys of the real
     * {@code candidate.EventPublisher#publishContactReleased}.
     */
    static final class CandidateSide {

        private CandidateSide() {
        }

        /** show_contacts=true with contacts ⇒ the release envelope. */
        static ContactReleasedEvent release(String eventId, String candidateId,
                                            String email, String phone, Instant releasedAt) {
            boolean showContacts = true;
            if (!showContacts || (email == null && phone == null)) {
                return null;
            }
            return new ContactReleasedEvent(eventId, ContactReleasedEvent.TYPE, 1,
                    releasedAt.toString(), "candidate-service", null,
                    new ContactReleasedEvent.Payload(candidateId, email, phone, releasedAt.toString()));
        }

        /** show_contacts=false ⇒ the candidate side emits nothing. */
        static ContactReleasedEvent releaseHidden(String eventId, String candidateId) {
            boolean showContacts = false;
            if (!showContacts) {
                return null;
            }
            return release(eventId, candidateId, null, null, Instant.now());
        }
    }
}
