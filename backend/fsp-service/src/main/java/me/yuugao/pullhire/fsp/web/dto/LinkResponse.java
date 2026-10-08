package me.yuugao.pullhire.fsp.web.dto;

import java.time.Instant;

import me.yuugao.pullhire.fsp.domain.FspLink;

/**
 * Current link state returned by {@code POST|DELETE /candidate/fsp/link}
 * ({@code docs/api.md §3.3}). Timestamps are UTC ISO-8601 ({@code docs/api.md §1}).
 */
public record LinkResponse(
        String candidateId,
        String fspId,
        String linkStatus,
        String historyStatus,
        String linkedAt,
        String lastSyncAt) {

    public static LinkResponse from(FspLink link) {
        return new LinkResponse(
                Long.toString(link.candidateId()),
                link.fspId(),
                link.linkStatus() == null ? null : link.linkStatus().wire(),
                link.historyStatus() == null ? null : link.historyStatus().wire(),
                iso(link.linkedAt()),
                iso(link.lastSyncAt()));
    }

    private static String iso(Instant value) {
        return value == null ? null : value.toString();
    }
}
