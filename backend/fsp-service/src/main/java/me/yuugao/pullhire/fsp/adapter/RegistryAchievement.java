package me.yuugao.pullhire.fsp.adapter;

import java.time.LocalDate;

/**
 * One achievement as returned by the registry seam, before it is mapped into
 * {@code fsp.achievements_cache}. {@code externalAchievementId} is the registry's
 * own identifier ({@code docs/data_model.md §5.2}); {@code payload} carries the raw
 * registry fragment for an unknown real structure ({@code docs/fsp_integration.md §7}).
 */
public record RegistryAchievement(
        String externalAchievementId,
        String title,
        String eventType,
        LocalDate achievedAt,
        Integer place,
        String payload) {
}
