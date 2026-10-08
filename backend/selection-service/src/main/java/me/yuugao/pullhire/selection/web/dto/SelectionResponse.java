package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formed подборка ({@code docs/api.md §3.5}, {@code docs/selection.md §4}):
 * mirrors the persisted {@code selection_results} snapshot (V6) — request
 * echo, recommended categories and items with explainability.
 * {@code parent_selection_id} is {@code NULL}: refinement creates a new row
 * pointing at the original (batch 5 owns reads/refinement, not this slice).
 */
public record SelectionResponse(
        long id,
        @JsonProperty("parent_selection_id") Long parentSelectionId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        SelectionRequest request,
        @JsonProperty("recommended_categories") List<String> recommendedCategories,
        List<SelectionItemResponse> items) {
}
