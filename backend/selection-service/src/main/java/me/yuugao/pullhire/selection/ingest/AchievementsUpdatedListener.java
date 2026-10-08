package me.yuugao.pullhire.selection.ingest;

import me.yuugao.pullhire.events.EventIdDedup;
import me.yuugao.pullhire.selection.config.AmqpConfig;
import me.yuugao.pullhire.selection.repo.ProcessedEventRepository;
import me.yuugao.pullhire.selection.repo.RankSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Consumer of {@code fsp.achievements_updated} on the selection-owned queue
 * ({@code docs/fsp_integration.md §5}, {@code docs/architecture.md §4.2}).
 * Re-ranks the candidate card: upserts {@code selection.rank_signals}
 * ({@code docs/data_model.md §7.4}) and mirrors the FSP fields into
 * {@code selection.candidate_cards} ({@code docs/data_model.md §7.3}).
 *
 * <p>Derivations ({@code docs/selection.md §3}):
 * {@code fsp_achievements_count = achievements.length};
 * {@code fsp_achievements_weighted} sums per-place weights;
 * {@code history_status=present} → {@code fsp_verified=true}, otherwise
 * ({@code unknown|no_history}) {@code fsp_verified=false} while count/weighted
 * stay as computed from the (possibly empty) achievements.
 *
 * <p>{@code history_status=no_history} is neutral, never a penalty
 * ({@code docs/fsp_integration.md §6}): no negative signal is written.
 *
 * <p>The event carries no {@code category_code} (and {@code rank_signals}
 * requires it NOT NULL), so the candidate's code is read from
 * {@code selection.candidate_categories}; when absent there is nothing to rank
 * yet and the delivery is a no-op.
 *
 * <p><b>Idempotency.</b> The event id is claimed once in
 * {@code selection.processed_events} (V2) inside the same transaction; a
 * duplicate is a no-op. The rank upsert itself is additionally conflict-safe
 * ({@code ON CONFLICT (candidate_id, category_code)}).
 */
@Component
public class AchievementsUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(AchievementsUpdatedListener.class);

    private static final String DEDUP_TABLE = "selection.processed_events";
    private static final String DEDUP_COLUMN = "source_event_id";

    /**
     * Per-place weights for {@code fsp_achievements_weighted}
     * ({@code docs/selection.md §3}, {@code docs/data_model.md §7.4}:
     * winner/placer &gt; plain participant). Illustrative defaults, OQ-1:
     * formula weights are not fixed by the docs and live in
     * {@code SelectionProperties} ({@code app.selection.rank.*}); these
     * per-place constants are placeholders of the same standing until OQ-1
     * calibration moves them into configuration.
     */
    static final double WEIGHT_WINNER = 1.0;
    static final double WEIGHT_PLACER = 0.5;
    static final double WEIGHT_PARTICIPANT = 0.25;

    private final EventIdDedup dedup;
    private final ProcessedEventRepository processedEvents;
    private final RankSignalRepository rankSignals;

    public AchievementsUpdatedListener(EventIdDedup dedup, ProcessedEventRepository processedEvents,
                                       RankSignalRepository rankSignals) {
        this.dedup = dedup;
        this.processedEvents = processedEvents;
        this.rankSignals = rankSignals;
    }

    @RabbitListener(queues = AmqpConfig.ACHIEVEMENTS_UPDATED_QUEUE)
    @Transactional
    public void onAchievementsUpdated(AchievementsUpdatedEvent event) {
        if (event == null || !AchievementsUpdatedEvent.TYPE.equals(event.type()) || event.payload() == null) {
            log.warn("Пропуск события без типа {} или payload", AchievementsUpdatedEvent.TYPE);
            return;
        }

        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank()
                && (dedup.isDuplicate(eventId, DEDUP_TABLE, DEDUP_COLUMN) || !processedEvents.claim(eventId))) {
            log.debug("Дубликат fsp.achievements_updated {} — пропуск", eventId);
            return;
        }

        Long candidateId = parseId(event.payload().candidateId());
        if (candidateId == null) {
            log.warn("Событие {} без корректного candidate_id — пропуск", eventId);
            return;
        }

        List<AchievementsUpdatedEvent.Achievement> achievements = event.payload().achievements();
        int count = achievements == null ? 0 : achievements.size();
        double weighted = weightedSum(achievements);
        boolean verified = "present".equals(event.payload().historyStatus());

        Optional<String> categoryCode = rankSignals.findCategoryCode(candidateId);
        if (categoryCode.isEmpty()) {
            log.debug("Категория candidate_id={} отсутствует — ранжировать нечего, пропуск", candidateId);
            return;
        }

        rankSignals.upsertFspSignals(candidateId, categoryCode.get(), verified, count, weighted);
        rankSignals.mirrorCardFspFields(candidateId, verified, count);
        log.info("rank_signals обновлены: candidate_id={} category={} fsp_verified={} count={} weighted={}",
                candidateId, categoryCode.get(), verified, count, weighted);
    }

    private static double weightedSum(List<AchievementsUpdatedEvent.Achievement> achievements) {
        if (achievements == null || achievements.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (AchievementsUpdatedEvent.Achievement achievement : achievements) {
            if (achievement == null || achievement.place() == null) {
                sum += WEIGHT_PARTICIPANT;
            } else if (achievement.place() == 1) {
                sum += WEIGHT_WINNER;
            } else {
                sum += WEIGHT_PLACER;
            }
        }
        return sum;
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
