package me.yuugao.pullhire.fsp.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.yuugao.pullhire.fsp.adapter.FspRegistryAdapter;
import me.yuugao.pullhire.fsp.adapter.RegistryAchievement;
import me.yuugao.pullhire.fsp.adapter.RegistryHistory;
import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.repo.AchievementCacheRepository;
import me.yuugao.pullhire.fsp.repo.FspLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FSP link/unlink use cases ({@code docs/api.md §3.3}, {@code docs/data_model.md §5},
 * {@code docs/fsp_integration.md §3/§6/§7}).
 *
 * <p>Link is idempotent on {@code UNIQUE (candidate_id)}: re-linking the same
 * {@code fsp_id} returns the current state and publishes nothing unless the state
 * actually changed. A {@code fsp_id} already bound to a different candidate is a
 * 409 conflict. An adapter failure yields {@code link_status = error} while a
 * successful empty history yields {@code history_status = no_history} — the two are
 * never conflated. Unlink is idempotent: absent/already-unlinked returns the no-op
 * state without an error.
 */
@Service
public class FspLinkService {

    private final FspLinkRepository links;
    private final AchievementCacheRepository achievements;
    private final FspRegistryAdapter registry;
    private final EventPublisher events;

    public FspLinkService(FspLinkRepository links, AchievementCacheRepository achievements,
                          FspRegistryAdapter registry, EventPublisher events) {
        this.links = links;
        this.achievements = achievements;
        this.registry = registry;
        this.events = events;
    }

    /** Binds (or re-binds) the caller's FSP id, syncing the cache and emitting events. */
    @Transactional
    public FspLink link(long candidateId, String fspId) {
        String normalized = normalize(fspId);
        FspLink existing = links.findByCandidateId(candidateId).orElse(null);

        links.findByFspId(normalized)
                .filter(other -> other.candidateId() != candidateId)
                .ifPresent(other -> {
                    throw ApiException.conflict("ФСП ID уже связан с другим кандидатом");
                });

        RegistryHistory history = registry.resolve(normalized);
        boolean failed = history.failed();
        LinkStatus linkStatus = failed ? LinkStatus.ERROR : LinkStatus.LINKED;
        // A transient adapter failure is NOT "no history": keep the last known
        // history_status (unknown only when there was no prior successful sync) and
        // preserve the last good achievement cache (docs/fsp_integration.md §6).
        HistoryStatus historyStatus = failed
                ? (existing == null ? HistoryStatus.UNKNOWN : existing.historyStatus())
                : history.status();
        List<RegistryAchievement> registryItems = failed ? List.of() : history.items();

        boolean sameFsp = existing != null && normalized.equals(existing.fspId());
        boolean stateChanged = existing == null
                || existing.linkStatus() == LinkStatus.UNLINKED
                || !sameFsp
                || existing.linkStatus() != linkStatus
                || existing.historyStatus() != historyStatus;

        Instant linkedAt = sameFsp && existing.linkedAt() != null ? existing.linkedAt() : Instant.now();
        long linkId = links.upsert(candidateId, normalized, linkStatus, historyStatus, linkedAt, Instant.now());

        List<AchievementCache> previous = List.of();
        boolean synced = !failed;
        if (synced) {
            previous = existing == null ? List.of() : achievements.listByLinkId(existing.id());
            achievements.replaceByLinkId(linkId, toCache(linkId, registryItems));
        }

        if (stateChanged) {
            events.publishLinked(candidateId, normalized, linkStatus, historyStatus);
        }
        if (synced && !sameAchievements(previous, registryItems)) {
            events.publishAchievementsUpdated(candidateId, normalized, historyStatus, registryItems);
        }
        return links.findByCandidateId(candidateId).orElseThrow();
    }

    /** Unbinds the caller's FSP id; absent/already-unlinked is a 200 no-op. */
    @Transactional
    public FspLink unlink(long candidateId) {
        FspLink existing = links.findByCandidateId(candidateId).orElse(null);
        if (existing == null) {
            return unlinked(candidateId, null);
        }
        if (existing.linkStatus() == LinkStatus.UNLINKED) {
            return existing;
        }
        achievements.replaceByLinkId(existing.id(), List.of());
        links.unlink(candidateId);
        FspLink updated = links.findByCandidateId(candidateId).orElseThrow();
        events.publishLinked(candidateId, existing.fspId(), LinkStatus.UNLINKED, HistoryStatus.UNKNOWN);
        // Drop stale achievements in selection-service: an empty set after the cache
        // is cleared (docs/fsp_integration.md §5/§6).
        events.publishAchievementsUpdated(candidateId, existing.fspId(), HistoryStatus.UNKNOWN, List.of());
        return updated;
    }

    private static FspLink unlinked(long candidateId, String fspId) {
        Instant now = Instant.now();
        return new FspLink(0L, candidateId, fspId, LinkStatus.UNLINKED, HistoryStatus.UNKNOWN,
                null, null, now, now);
    }

    private static String normalize(String fspId) {
        if (fspId == null || fspId.isBlank()) {
            throw ApiException.validation("fsp_id обязателен");
        }
        return fspId.trim();
    }

    private static List<AchievementCache> toCache(long fspLinkId, List<RegistryAchievement> items) {
        return items.stream()
                .map(item -> new AchievementCache(0L, fspLinkId, item.externalAchievementId(), item.title(),
                        item.eventType(), item.achievedAt(), item.place(), item.payload(), null))
                .toList();
    }

    /**
     * Compares the cached set with the freshly resolved one as a multiset keyed by
     * {@code external_achievement_id}, so a reordering of the registry response is
     * not mistaken for a change.
     */
    private static boolean sameAchievements(List<AchievementCache> previous, List<RegistryAchievement> resolved) {
        if (previous.size() != resolved.size()) {
            return false;
        }
        Map<String, RegistryAchievement> byExternalId = new HashMap<>();
        for (RegistryAchievement fresh : resolved) {
            byExternalId.put(key(fresh.externalAchievementId()), fresh);
        }
        for (AchievementCache cached : previous) {
            RegistryAchievement fresh = byExternalId.get(key(cached.externalAchievementId()));
            if (fresh == null
                    || !Objects.equals(cached.title(), fresh.title())
                    || !Objects.equals(cached.eventType(), fresh.eventType())
                    || !Objects.equals(cached.achievedAt(), fresh.achievedAt())
                    || !Objects.equals(cached.place(), fresh.place())) {
                return false;
            }
        }
        return true;
    }

    private static String key(String externalAchievementId) {
        return externalAchievementId == null ? "" : externalAchievementId;
    }
}
