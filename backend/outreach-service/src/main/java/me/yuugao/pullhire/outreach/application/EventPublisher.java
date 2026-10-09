package me.yuugao.pullhire.outreach.application;

import java.util.LinkedHashMap;
import java.util.Map;

import me.yuugao.pullhire.events.CorrelationMdcFilter;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Publishes invitation lifecycle events through the shared transactional
 * outbox ({@code docs/architecture.md §4.2/§4.3}). {@link OutboxEventPublisher}
 * is the only publish path, so each event commits atomically with its state
 * change. Identifiers are strings ({@code docs/api.md §1}); correlation id is
 * taken from MDC, which {@link CorrelationMdcFilter} fills from
 * {@code X-Request-Id}.
 */
@Component
public class EventPublisher {

    private static final String PRODUCER = "outreach-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    /** {@code invitation.sent}: emitted when an employer sends an invitation. */
    public void publishSent(long invitationId, long candidateId, long employerId) {
        outbox.publish(EventEnvelope.of(EventNames.INVITATION_SENT, PRODUCER,
                correlationId(), invitationPayload(invitationId, candidateId, employerId)));
    }

    /** {@code invitation.viewed}: emitted when the candidate views an invitation. */
    public void publishViewed(long invitationId, long candidateId, long employerId) {
        outbox.publish(EventEnvelope.of(EventNames.INVITATION_VIEWED, PRODUCER,
                correlationId(), invitationPayload(invitationId, candidateId, employerId)));
    }

    /** {@code invitation.accepted}: emitted when the candidate accepts (no contact disclosure here). */
    public void publishAccepted(long invitationId, long candidateId, long employerId) {
        outbox.publish(EventEnvelope.of(EventNames.INVITATION_ACCEPTED, PRODUCER,
                correlationId(), invitationPayload(invitationId, candidateId, employerId)));
    }

    /** {@code invitation.declined}: emitted when the candidate declines. */
    public void publishDeclined(long invitationId, long candidateId, long employerId) {
        outbox.publish(EventEnvelope.of(EventNames.INVITATION_DECLINED, PRODUCER,
                correlationId(), invitationPayload(invitationId, candidateId, employerId)));
    }

    private static Map<String, Object> invitationPayload(long invitationId, long candidateId, long employerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invitation_id", String.valueOf(invitationId));
        payload.put("candidate_id", String.valueOf(candidateId));
        payload.put("employer_id", String.valueOf(employerId));
        return payload;
    }

    private static String correlationId() {
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
