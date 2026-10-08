package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Paginated list envelope ({@code docs/api.md §1}). */
public record PageResponse<T>(
        @JsonProperty("items") List<T> items,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total") long total) {
}
