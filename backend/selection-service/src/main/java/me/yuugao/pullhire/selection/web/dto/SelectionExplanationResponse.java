package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Qualitative explainability of one подборка item
 * ({@code docs/selection.md §4.3}): why the candidate is in the output and
 * why they hold their position. Qualitative strings only — no {@code theta},
 * no score ({@code docs/testing_model.md §8}: attempt results are never
 * disclosed to the employer).
 */
public record SelectionExplanationResponse(
        @JsonProperty("category_match") String categoryMatch,
        @JsonProperty("confirmed_test_basis") String confirmedTestBasis,
        @JsonProperty("fsp_achievements") String fspAchievements,
        @JsonProperty("stack_match") String stackMatch) {
}
