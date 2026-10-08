package me.yuugao.pullhire.fsp.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.events.CorrelationMdcFilter;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.fsp.adapter.RegistryAchievement;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code fsp.linked} and {@code fsp.achievements_updated} through the
 * shared transactional outbox ({@code docs/architecture.md §4.2/§4.3},
 * {@code docs/fsp_integration.md §4}). {@link OutboxEventPublisher} is the only
 * publish path, so events commit atomically with the link/cache change.
 *
 * <p>Payload keys match the committed contract schemas exactly
 * ({@code contracts/events/fsp.linked.schema.json},
 * {@code fsp.achievements_updated.schema.json}). Correlation id is taken from MDC,
 * which {@link CorrelationMdcFilter} fills from {@code X-Request-Id}.
 */
@Component
public class EventPublisher {

    private static final String PRODUCER = "fsp-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    /** {@code fsp.linked}: emitted on any link-state change, including unlink and error. */
    public void publishLinked(long candidateId, String fspId, LinkStatus linkStatus, HistoryStatus historyStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", Long.toString(candidateId));
        payload.put("fsp_id", fspId);
        payload.put("link_status", linkStatus == null ? null : linkStatus.wire());
        payload.put("history_status", historyStatus == null ? null : historyStatus.wire());

        outbox.publish(EventEnvelope.of(EventNames.FSP_LINKED, PRODUCER, correlationId(), payload));
    }

    /** {@code fsp.achievements_updated}: emitted when the cached achievement set changes. */
    public void publishAchievementsUpdated(long candidateId, String fspId, HistoryStatus historyStatus,
                                           List<RegistryAchievement> achievements) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (achievements != null) {
            for (RegistryAchievement achievement : achievements) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("external_achievement_id", achievement.externalAchievementId());
                item.put("title", achievement.title());
                item.put("event_type", achievement.eventType());
                item.put("achieved_at", date(achievement.achievedAt()));
                item.put("place", achievement.place());
                items.add(item);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate_id", Long.toString(candidateId));
        payload.put("fsp_id", fspId);
        payload.put("history_status", historyStatus == null ? null : historyStatus.wire());
        payload.put("achievements", items);

        outbox.publish(EventEnvelope.of(EventNames.FSP_ACHIEVEMENTS_UPDATED, PRODUCER, correlationId(), payload));
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static String correlationId() {
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
