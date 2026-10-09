package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /internal/candidates/{id}/category} response ({@code docs/api.md §3.5}). */
public record CandidateCategoryInternalResponse(
        @JsonProperty("candidate_id") String candidateId,
        @JsonProperty("specialization_code") String specializationCode,
        @JsonProperty("grade") String grade,
        @JsonProperty("category_code") String categoryCode) {
}
