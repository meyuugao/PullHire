package me.yuugao.pullhire.selection.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Candidate card for {@code GET /candidates} ({@code docs/api.md §3.5}).
 * Projection of {@code selection.candidate_cards ⋈ candidate_categories}
 * ({@code docs/data_model.md §7.2}, {@code §7.3}); no contact/PII fields
 * ({@code docs/data_model.md §12}).
 */
public record CandidateCardResponse(
        @JsonProperty("candidate_id") long candidateId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("specialization_code") String specializationCode,
        @JsonProperty("grade") String grade,
        @JsonProperty("stack") List<String> stack,
        @JsonProperty("fsp_verified") boolean fspVerified,
        @JsonProperty("fsp_achievements_count") int fspAchievementsCount,
        @JsonProperty("category_confirmed") boolean categoryConfirmed,
        @JsonProperty("category_grade") String categoryGrade) {

    public static List<String> toList(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }
}
