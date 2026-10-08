package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /assessment/grade-choice} body ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §2.2}): the session to fix and the
 * candidate's explicit grade choice.
 *
 * <p>Wire shape follows {@code docs/api.md §1}: {@code snake_case} keys and
 * stringified identifiers (like {@code SurveyResponse}/{@code SessionStartResponse}).
 */
public record GradeChoiceRequest(
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("declared_grade") @NotBlank String declaredGrade) {
}
