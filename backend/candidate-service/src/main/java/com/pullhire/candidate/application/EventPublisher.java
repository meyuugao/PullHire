package com.pullhire.candidate.application;

import com.pullhire.events.CorrelationMdcFilter;
import com.pullhire.events.EventEnvelope;
import com.pullhire.events.EventNames;
import com.pullhire.events.OutboxEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes {@code candidate.card_updated} and {@code candidate.contact_released}
 * through the shared transactional outbox ({@code docs/architecture.md §4.2/§4.3}).
 * {@link OutboxEventPublisher} is the only publish path, so events commit with the
 * state change.
 *
 * <p>Payload keys match the committed contract schemas exactly
 * ({@code contracts/events/candidate.card_updated.schema.json},
 * {@code candidate.contact_released.schema.json}); the card payload carries no PII
 * (no email/phone/full_name - {@code docs/data_model.md §12}). Correlation id is
 * taken from MDC, which {@link CorrelationMdcFilter} fills from {@code X-Request-Id}
 * (generating one if absent).
 */
@Component
public class EventPublisher {

    private static final String PRODUCER = "candidate-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    public void publishCardUpdated(String candidateId, boolean visibleToEmployers, String displayName,
                                   String headline, String industryCode, String specializationCode,
                                   String grade, List<String> stack, boolean fspVerified,
                                   boolean contactReleased) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", candidateId);
        payload.put("visible_to_employers", visibleToEmployers);
        payload.put("display_name", displayName);
        payload.put("headline", headline);
        payload.put("industry_code", industryCode);
        payload.put("specialization_code", specializationCode);
        payload.put("grade", grade);
        payload.put("stack", stack == null ? List.of() : stack);
        payload.put("fsp_verified", fspVerified);
        payload.put("contact_released", contactReleased);

        outbox.publish(EventEnvelope.of(EventNames.CANDIDATE_CARD_UPDATED, PRODUCER, correlationId(), payload));
    }

    public void publishContactReleased(String candidateId, String contactEmail, String contactPhone,
                                       Instant releasedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", candidateId);
        payload.put("contact_email", contactEmail);
        payload.put("contact_phone", contactPhone);
        payload.put("released_at", releasedAt == null ? null : releasedAt.toString());

        outbox.publish(EventEnvelope.of(EventNames.CANDIDATE_CONTACT_RELEASED, PRODUCER, correlationId(), payload));
    }

    private static String correlationId() {
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
