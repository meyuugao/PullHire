package me.yuugao.pullhire.fsp.web.dto;

import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;

/**
 * {@code GET /internal/fsp/{candidateId}} response ({@code docs/api.md §3.3}):
 * internal read of a link plus its cached achievements for other services.
 * Not published through the gateway.
 */
public record InternalFspResponse(
        String candidateId,
        String fspId,
        String linkStatus,
        String historyStatus,
        String linkedAt,
        String lastSyncAt,
        List<AchievementItem> items) {

    public static InternalFspResponse from(FspLink link, List<AchievementCache> achievements) {
        return new InternalFspResponse(
                Long.toString(link.candidateId()),
                link.fspId(),
                link.linkStatus() == null ? null : link.linkStatus().wire(),
                link.historyStatus() == null ? null : link.historyStatus().wire(),
                iso(link.linkedAt()),
                iso(link.lastSyncAt()),
                achievements.stream().map(AchievementItem::from).toList());
    }

    private static String iso(Instant value) {
        return value == null ? null : value.toString();
    }
}
