package me.yuugao.pullhire.selection.ingest;

import java.time.Instant;
import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.config.AmqpConfig;
import me.yuugao.pullhire.selection.repo.CandidateCategoryRepository;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer of {@code assessment.category_assigned} on
 * {@code selection.category-assigned} (TD-1, {@code docs/architecture.md §3.1
 * row 4, §4.3}). Projects the current category into
 * {@code selection.candidate_categories}; every completed attempt republishes,
 * so each delivery overwrites the row (upsert).
 *
 * <p><b>Idempotency.</b> The envelope {@code event_id} is claimed once in
 * {@code selection.processed_events} (as {@code source_event_id}) inside the
 * same transaction; a duplicate is a no-op. The upsert itself is additionally
 * overwrite-idempotent ({@code ON CONFLICT (candidate_id) DO UPDATE}).
 *
 * <p>{@code assessment.grade_changed} is audit-only — no listener is created
 * for it.
 */
@Component
public class CategoryAssignedListener {

    private static final Logger log = LoggerFactory.getLogger(CategoryAssignedListener.class);

    private static final String DEDUP_TABLE = "selection.processed_events";
    private static final String DEDUP_COLUMN = "source_event_id";

    private final EventIdDedup dedup;
    private final ProcessedEventRepository processedEvents;
    private final CandidateCategoryRepository categories;

    public CategoryAssignedListener(EventIdDedup dedup, ProcessedEventRepository processedEvents,
                                    CandidateCategoryRepository categories) {
        this.dedup = dedup;
        this.processedEvents = processedEvents;
        this.categories = categories;
    }

    @RabbitListener(queues = AmqpConfig.CATEGORY_ASSIGNED_QUEUE)
    @Transactional
    public void onCategoryAssigned(CategoryAssignedEvent event) {
        if (event == null || !CategoryAssignedEvent.TYPE.equals(event.type()) || event.payload() == null) {
            log.warn("Пропуск события без типа {} или payload", CategoryAssignedEvent.TYPE);
            return;
        }

        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank()
                && (dedup.isDuplicate(eventId, DEDUP_TABLE, DEDUP_COLUMN) || !processedEvents.claim(eventId))) {
            log.debug("Дубликат assessment.category_assigned {} — пропуск", eventId);
            return;
        }

        CategoryAssignedEvent.Payload payload = event.payload();
        Long candidateId = parseId(payload.candidateId());
        if (candidateId == null) {
            log.warn("Событие {} без корректного candidate_id — пропуск", eventId);
            return;
        }
        if (payload.specializationCode() == null || payload.specializationCode().isBlank()
                || payload.grade() == null || payload.grade().isBlank()
                || payload.categoryCode() == null || payload.categoryCode().isBlank()
                || payload.assignedAt() == null || payload.assignedAt().isBlank()) {
            log.warn("Событие {} с неполным payload — пропуск", eventId);
            return;
        }

        Instant assignedAt;
        try {
            assignedAt = Instant.parse(payload.assignedAt().trim());
        } catch (Exception ex) {
            log.warn("Событие {} с некорректным assigned_at — пропуск", eventId);
            return;
        }

        categories.upsert(candidateId, payload.specializationCode(), payload.grade(),
                payload.categoryCode(), eventId, assignedAt);
        log.info("Категория обновлена: candidate_id={} category={}", candidateId, payload.categoryCode());
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
}
