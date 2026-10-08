package me.yuugao.pullhire.fsp.domain;

import java.time.Instant;

/**
 * Row of {@code fsp.fsp_links} ({@code docs/data_model.md §5.1}).
 * {@code candidateId} is a logical reference to {@code candidate.profiles.id}
 * (no cross-schema FK).
 */
public record FspLink(
        long id,
        long candidateId,
        String fspId,
        LinkStatus linkStatus,
        HistoryStatus historyStatus,
        Instant linkedAt,
        Instant lastSyncAt,
        Instant createdAt,
        Instant updatedAt) {
}
