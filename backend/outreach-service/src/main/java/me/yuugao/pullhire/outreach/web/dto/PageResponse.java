package me.yuugao.pullhire.outreach.web.dto;

import java.util.List;

/** Paged list envelope ({@code docs/api.md §1}). */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total) {
}
