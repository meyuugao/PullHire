package me.yuugao.pullhire.fsp.web.dto;

import java.util.List;

import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;

/**
 * {@code GET /candidate/fsp/achievements} response ({@code docs/api.md §3.3}).
 * {@code historyStatus = no_history} returns 200 with an empty {@code items} list —
 * distinct from "data unavailable" ({@code docs/fsp_integration.md §6}).
 */
public record AchievementsResponse(
        String candidateId,
        String fspId,
        String linkStatus,
        String historyStatus,
        List<AchievementItem> items) {

    public static AchievementsResponse from(FspLink link, List<AchievementCache> achievements) {
        return new AchievementsResponse(
                Long.toString(link.candidateId()),
                link.fspId(),
                link.linkStatus() == null ? null : link.linkStatus().wire(),
                link.historyStatus() == null ? null : link.historyStatus().wire(),
                achievements.stream().map(AchievementItem::from).toList());
    }
}
