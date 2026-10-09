package me.yuugao.pullhire.outreach.ingest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.outreach.config.AmqpConfig;
import me.yuugao.pullhire.outreach.repo.InvitationEventRepository;
import me.yuugao.pullhire.outreach.repo.InvitationRepository;
import me.yuugao.pullhire.outreach.repo.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer of {@code candidate.contact_released} on
 * {@code outreach.candidate-contact-released}
 * ({@code docs/architecture.md §4.2}). This is the ONLY path that fills
 * {@code invitations.released_contact}: every accepted, not-yet-released
 * invitation of the candidate gets the snapshot plus one
 * {@code contact_released} audit row (actor {@code system}).
 *
 * <p><b>Idempotency.</b> The envelope {@code event_id} is claimed once in
 * {@code outreach.processed_events} (as {@code source_event_id}) inside the
 * same transaction; a duplicate is a no-op. A delivery with no accepted
 * invitations is marked processed with no further writes.
 */
@Component
public class ContactReleasedListener {

    private static final Logger log = LoggerFactory.getLogger(ContactReleasedListener.class);

    private static final JsonMapper JSON = new JsonMapper();

    private final ProcessedEventRepository processedEvents;
    private final InvitationRepository invitations;
    private final InvitationEventRepository events;

    public ContactReleasedListener(ProcessedEventRepository processedEvents,
                                   InvitationRepository invitations,
                                   InvitationEventRepository events) {
        this.processedEvents = processedEvents;
        this.invitations = invitations;
        this.events = events;
    }

    @RabbitListener(queues = AmqpConfig.CONTACT_RELEASED_QUEUE)
    @Transactional
    public void onContactReleased(ContactReleasedEvent event) {
        if (event == null || !ContactReleasedEvent.TYPE.equals(event.type()) || event.payload() == null) {
            log.warn("Пропуск события без типа {} или payload", ContactReleasedEvent.TYPE);
            return;
        }

        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank() && !processedEvents.claim(eventId)) {
            log.debug("Дубликат candidate.contact_released {} — пропуск", eventId);
            return;
        }

        ContactReleasedEvent.Payload payload = event.payload();
        Long candidateId = parseId(payload.candidateId());
        if (candidateId == null) {
            log.warn("Событие {} без корректного candidate_id — пропуск", eventId);
            return;
        }
        Instant releasedAt = parseInstant(payload.releasedAt());
        if (releasedAt == null) {
            log.warn("Событие {} с некорректным released_at — пропуск", eventId);
            return;
        }

        List<Long> pending = invitations.findAcceptedIdsPendingRelease(candidateId);
        if (pending.isEmpty()) {
            log.debug("candidate.contact_released {}: нет принятых приглашений candidate_id={} — no-op",
                    eventId, candidateId);
            return;
        }

        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("contact_email", blankToNull(payload.contactEmail()));
        contact.put("contact_phone", blankToNull(payload.contactPhone()));
        String releasedContactJson = JSON.writeValueAsString(contact);

        invitations.markReleased(candidateId, releasedContactJson, releasedAt);
        for (Long invitationId : pending) {
            events.insert(invitationId, "contact_released", "system",
                    JSON.writeValueAsString(Map.of("invitation_id", String.valueOf(invitationId))));
        }
        log.info("Контакты раскрыты: candidate_id={} invitations={}", candidateId, pending.size());
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
