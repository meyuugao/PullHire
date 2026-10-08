package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /selections} body ({@code docs/api.md §3.5},
 * {@code docs/selection.md §4.1}). Mirrors the employer {@code NeedRequest}
 * fields minus {@code status}: the need description is the input document for
 * forming a подборка.
 */
public record SelectionRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotBlank @JsonProperty("specialization_code") String specializationCode,
        @NotBlank String grade,
        List<String> stack,
        /**
         * Optional link to the original подборка this request refines
         * (a new {@code POST}; originals are never overwritten).
         *
         * <p>OQ-style note: refinement chain depth is unrestricted by design —
         * docs do not restrict depth, the parent only needs to pre-exist and
         * snapshots are immutable INSERT-only, so cycles are structurally
         * impossible. Each chain level costs one {@code findById}; no depth
         * limit is enforced.
         */
        @JsonProperty("parent_selection_id") Long parentSelectionId) {
}
