package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One ranked candidate of a подборка: id, 1-based position and the
 * qualitative explanation ({@code docs/selection.md §4.3}). No card
 * enrichment, no {@code theta} (V6 snapshot design).
 */
public record SelectionItemResponse(
        @JsonProperty("candidate_id") long candidateId,
        int position,
        SelectionExplanationResponse explanation) {
}
