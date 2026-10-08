package me.yuugao.pullhire.fsp.web.dto;

import java.time.LocalDate;

import me.yuugao.pullhire.fsp.domain.AchievementCache;

/** One cached achievement in the fsp read responses ({@code docs/fsp_integration.md §5}). */
public record AchievementItem(
        String externalAchievementId,
        String title,
        String eventType,
        String achievedAt,
        Integer place) {

    public static AchievementItem from(AchievementCache achievement) {
        return new AchievementItem(
                achievement.externalAchievementId(),
                achievement.title(),
                achievement.eventType(),
                date(achievement.achievedAt()),
                achievement.place());
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
