package me.yuugao.pullhire.selection.ingest;

import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.config.AmqpConfig;
import me.yuugao.pullhire.selection.repo.CandidateCardRepository;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Consumer of {@code candidate.card_updated} on {@code selection.card-updated}
 * ({@code docs/architecture.md §6.2}). Maintains the search projection
 * {@code selection.candidate_cards} ({@code docs/data_model.md §7.3}, no PII).
 *
 * <p>{@code display_name} arrives already masked by the publisher — stored as-is.
 * {@code fsp_achievements_count} is not in this payload and is left untouched
 * (mirrored by ST-7.10).
 *
 * <p><b>Contract null-handling.</b> {@code visible_to_employers=false} deletes the
 * card row (candidate must not be searchable). A null {@code specialization_code}
 * or {@code grade} is non-upsertable — the upsert is skipped (no partial rows).
 * Null {@code stack} entries are filtered out.
 *
 * <p><b>Idempotency (OQ-22).</b> The event id is claimed once in
 * {@code selection.processed_events} (V2) inside the same transaction; a duplicate
 * is a no-op.
 */
@Component
public class CardUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(CardUpdatedListener.class);

    private static final String DEDUP_TABLE = "selection.processed_events";
    private static final String DEDUP_COLUMN = "source_event_id";

    private final EventIdDedup dedup;
    private final ProcessedEventRepository processedEvents;
    private final CandidateCardRepository cards;

    public CardUpdatedListener(EventIdDedup dedup, ProcessedEventRepository processedEvents,
                               CandidateCardRepository cards) {
        this.dedup = dedup;
        this.processedEvents = processedEvents;
        this.cards = cards;
    }

    @RabbitListener(queues = AmqpConfig.CARD_UPDATED_QUEUE)
    @Transactional
    public void onCardUpdated(CardUpdatedEvent event) {
        if (event == null || !CardUpdatedEvent.TYPE.equals(event.type()) || event.payload() == null) {
            log.warn("Пропуск события без типа {} или payload", CardUpdatedEvent.TYPE);
            return;
        }

        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank()
                && (dedup.isDuplicate(eventId, DEDUP_TABLE, DEDUP_COLUMN) || !processedEvents.claim(eventId))) {
            log.debug("Дубликат candidate.card_updated {} — пропуск", eventId);
            return;
        }

        CardUpdatedEvent.Payload payload = event.payload();
        Long candidateId = parseId(payload.candidateId());
        if (candidateId == null) {
            log.warn("Событие {} без корректного candidate_id — пропуск", eventId);
            return;
        }

        if (payload.visibleToEmployers() == null) {
            log.warn("Событие {} без visible_to_employers — пропуск (нарушение контракта)", eventId);
            return;
        }

        if (Boolean.FALSE.equals(payload.visibleToEmployers())) {
            cards.deleteByCandidateId(candidateId);
            log.debug("Карточка удалена: candidate_id={} (visible_to_employers=false)", candidateId);
            return;
        }

        if (payload.specializationCode() == null || payload.grade() == null
                || payload.displayName() == null) {
            log.warn("Карточка не upsertable: candidate_id={} (specialization_code/grade/display_name is null)", candidateId);
            return;
        }

        List<String> stack = payload.stack() == null ? List.of()
                : payload.stack().stream().filter(Objects::nonNull).toList();
        cards.upsert(candidateId, payload.displayName(), payload.specializationCode(),
                payload.grade(), stack.toArray(String[]::new),
                Boolean.TRUE.equals(payload.fspVerified()));
        log.debug("Карточка обновлена: candidate_id={}", candidateId);
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
