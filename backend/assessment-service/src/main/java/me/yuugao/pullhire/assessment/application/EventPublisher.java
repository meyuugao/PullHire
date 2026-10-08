package me.yuugao.pullhire.assessment.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.events.CorrelationMdcFilter;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code assessment.category_assigned} and {@code assessment.grade_changed}
 * through the shared transactional outbox ({@code docs/architecture.md §3.1 row 4,
 * §4.2/§4.3}; {@code docs/testing_model.md §7.1}). {@link OutboxEventPublisher} is the
 * only publish path, so events commit atomically with the grade/category change and
 * {@code OutboxRelay} delivers them afterwards — no direct {@code RabbitTemplate}.
 *
 * <p>Payload keys match the committed contract schemas exactly
 * ({@code contracts/events/assessment.category_assigned.schema.json},
 * {@code assessment.grade_changed.schema.json}). Correlation id is taken from MDC,
 * which {@link CorrelationMdcFilter} fills from {@code X-Request-Id}.
 */
@Component
public class EventPublisher {

    private static final String PRODUCER = "assessment-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    /**
     * {@code assessment.category_assigned}: emitted on every completed attempt (up,
     * down or repeated) with the current category; θ is deliberately excluded.
     * {@code sessionId} is nullable on the manual path; {@code assignedAt} is always
     * emitted non-null (defaults to {@code Instant.now()} when absent).
     */
    public void publishCategoryAssigned(long candidateId, String specializationCode, String grade,
                                        String categoryCode, String sessionId, Instant assignedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", Long.toString(candidateId));
        payload.put("specialization_code", specializationCode);
        payload.put("grade", grade);
        payload.put("category_code", categoryCode);
        payload.put("session_id", sessionId);
        payload.put("assigned_at", (assignedAt == null ? Instant.now() : assignedAt).toString());

        outbox.publish(EventEnvelope.of(EventNames.ASSESSMENT_CATEGORY_ASSIGNED, PRODUCER,
                correlationId(), payload));
    }

    /**
     * {@code assessment.grade_changed}: audit/notification-only; mirrors an
     * {@code assessment.grade_history} row and does not update the category projection.
     * {@code categoryCode}, {@code sessionId} and {@code reason} are nullable.
     */
    public void publishGradeChanged(long candidateId, String specializationCode, String grade,
                                    GradeDirection direction, String categoryCode, String sessionId,
                                    String reason) {
        java.util.Objects.requireNonNull(direction, "direction");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", Long.toString(candidateId));
        payload.put("specialization_code", specializationCode);
        payload.put("grade", grade);
        payload.put("direction", direction.wire());
        payload.put("category_code", categoryCode);
        payload.put("session_id", sessionId);
        payload.put("reason", reason);

        outbox.publish(EventEnvelope.of(EventNames.ASSESSMENT_GRADE_CHANGED, PRODUCER,
                correlationId(), payload));
    }

    private static String correlationId() {
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
