package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /catalog/categories} item ({@code docs/api.md §3.5}). */
public record CategoryResponse(
        @JsonProperty("category_code") String categoryCode,
        @JsonProperty("specialization_code") String specializationCode,
        @JsonProperty("specialization_name") String specializationName,
        @JsonProperty("grade_code") String gradeCode,
        @JsonProperty("grade_name") String gradeName) {}
