package me.yuugao.pullhire.fsp.adapter;

import java.time.LocalDate;
import java.util.List;

import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock registry for the hackathon ({@code docs/fsp_integration.md §4}).
 * Active by default ({@code app.fsp.adapter=mock}, {@code matchIfMissing=true}).
 *
 * <p>Reserved test fixtures (not part of the API contract):
 * <ul>
 *   <li>{@code error} (case-insensitive) — simulates a registry failure so the service
 *       sets {@code link_status = error} and keeps {@code history_status = unknown};</li>
 *   <li>{@code no_history} (case-insensitive) — a successful resolve with no
 *       achievements ({@code docs/fsp_integration.md §6});</li>
 *   <li>any other id — {@code present} with a deterministic synthetic achievement set,
 *       always filling {@code external_achievement_id}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.fsp.adapter", havingValue = "mock", matchIfMissing = true)
public class MockFspRegistryAdapter implements FspRegistryAdapter {

    /** Reserved fixture: force an adapter failure ({@code link_status = error}). */
    public static final String ERROR_MARKER = "error";

    /** Reserved fixture: successful resolve with an empty history. */
    public static final String NO_HISTORY_MARKER = "no_history";

    @Override
    public RegistryHistory resolve(String fspId) {
        String id = fspId == null ? "" : fspId.trim();
        if (ERROR_MARKER.equalsIgnoreCase(id)) {
            return RegistryHistory.error();
        }
        if (NO_HISTORY_MARKER.equalsIgnoreCase(id)) {
            return RegistryHistory.of(HistoryStatus.NO_HISTORY, List.of());
        }
        return RegistryHistory.of(HistoryStatus.PRESENT, List.of(
                new RegistryAchievement(id + "-1", "Победа в отборе ФСП", "championship",
                        LocalDate.of(2023, 5, 14), 1, null),
                new RegistryAchievement(id + "-2", "Участие в отборе ФСП", "selection",
                        LocalDate.of(2022, 9, 3), null, null)));
    }
}
