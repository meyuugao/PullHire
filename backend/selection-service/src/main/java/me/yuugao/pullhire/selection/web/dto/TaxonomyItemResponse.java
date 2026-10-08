package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /catalog/specializations|/catalog/grades} item ({@code docs/api.md §3.5}). */
public record TaxonomyItemResponse(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("grade_order") Integer gradeOrder) {}
