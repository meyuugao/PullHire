package me.yuugao.pullhire.fsp.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Row of {@code fsp.achievements_cache} ({@code docs/data_model.md §5.2}).
 * {@code payload} is the raw {@code jsonb} text (the real registry structure is
 * unknown, {@code docs/fsp_integration.md §7}); the typed columns
 * ({@code title}/{@code eventType}/{@code achievedAt}/{@code place}) are the stable
 * display contract.
 */
public record AchievementCache(
        long id,
        long fspLinkId,
        String externalAchievementId,
        String title,
        String eventType,
        LocalDate achievedAt,
        Integer place,
        String payload,
        Instant syncedAt) {
}
