package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /assessment/grade-choice} response ({@code docs/api.md §3.4}):
 * the fixed declared grade and the routing start θ now stored on the session.
 *
 * <p>Wire shape follows {@code docs/api.md §1}: {@code snake_case} keys and
 * stringified identifiers (like {@code SurveyResponse}/{@code SessionStartResponse}).
 * The result path never downgrades forcibly
 * ({@code docs/testing_model.md §7.2}): a lower choice is candidate-initiated
 * and applied as-is.
 */
public record GradeChoiceResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("declared_grade") String declaredGrade,
        @JsonProperty("start_theta") double startTheta) {

    public static GradeChoiceResponse from(long sessionId, String declaredGrade, double startTheta) {
        return new GradeChoiceResponse(Long.toString(sessionId), declaredGrade, startTheta);
    }
}
