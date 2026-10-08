package me.yuugao.pullhire.fsp.adapter;

import java.util.List;

import me.yuugao.pullhire.fsp.domain.HistoryStatus;

/**
 * Result of an {@link FspRegistryAdapter#resolve(String)} call: the history
 * {@link HistoryStatus} plus the achievements, or a technical {@code error}.
 *
 * <p>A successful resolve returns {@link #of(HistoryStatus, List)} with
 * {@code present} + items, or {@code no_history} + an empty list. An adapter that
 * could not reach the registry returns {@link #error()}; the service maps that to
 * {@code link_status = error}, never to {@code no_history}
 * ({@code docs/fsp_integration.md §6}).
 */
public record RegistryHistory(HistoryStatus status, List<RegistryAchievement> items, boolean failed) {

    public RegistryHistory {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static RegistryHistory of(HistoryStatus status, List<RegistryAchievement> items) {
        return new RegistryHistory(status, items, false);
    }

    public static RegistryHistory error() {
        return new RegistryHistory(HistoryStatus.UNKNOWN, List.of(), true);
    }
}
