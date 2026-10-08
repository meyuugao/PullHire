package me.yuugao.pullhire.employer.application;

import java.util.LinkedHashMap;
import java.util.Map;

import me.yuugao.pullhire.events.CorrelationMdcFilter;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code employer.need_deleted} through the shared transactional
 * outbox ({@code docs/architecture.md §4.2/§4.3}, {@code docs/api.md §3.6}).
 * {@link OutboxEventPublisher} is the only publish path, so the event commits
 * atomically with the need delete.
 *
 * <p>Payload keys match the committed contract schema exactly
 * ({@code contracts/events/employer.need_deleted.schema.json}). Identifiers
 * are strings ({@code docs/api.md §1}). Correlation id is taken from MDC,
 * which {@link CorrelationMdcFilter} fills from {@code X-Request-Id}.
 */
@Component
public class EventPublisher {

    private static final String PRODUCER = "employer-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    /** {@code employer.need_deleted}: emitted when a hiring need is deleted. */
    public void publishNeedDeleted(String needId, String companyId, String userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", needId);
        payload.put("company_id", companyId);
        payload.put("user_id", userId);

        outbox.publish(EventEnvelope.of(EventNames.EMPLOYER_NEED_DELETED, PRODUCER,
                correlationId(), payload));
    }

    private static String correlationId() {
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
