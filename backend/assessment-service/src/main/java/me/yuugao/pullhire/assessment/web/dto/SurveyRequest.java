package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /assessment/survey} body ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §2.1}, {@code docs/backlog/closed_questions.md TD-3}).
 *
 * <p>The candidate id is taken from the JWT, never from the body.
 * {@code survey_answers} is stored raw as {@code test_sessions.survey_answers}
 * (jsonb) and {@code declared_dimensions} as {@code test_sessions.declared_dimensions}
 * (text[]). {@code specialization_code} and {@code target_grade} complete the
 * session identity required to open the row; {@code industry_code} is optional.
 *
 * <p><strong>Intentional MVP merge of survey + grade-choice:</strong>
 * {@code target_grade} is collected here because
 * {@code test_sessions.target_grade} is {@code NOT NULL}, even though
 * {@code docs/testing_model.md §2.2} defines grade-choice as a separate stage.
 * {@code POST /assessment/grade-choice} (S5.19) later confirms/sets
 * {@code declared_grade}; no schema change is intended.
 */
public record SurveyRequest(
        @JsonProperty("industry_code") String industryCode,
        @JsonProperty("specialization_code") @NotBlank String specializationCode,
        @JsonProperty("target_grade") @NotBlank String targetGrade,
        @JsonProperty("declared_dimensions") @NotEmpty List<String> declaredDimensions,
        @JsonProperty("survey_answers") @NotNull Map<String, Object> surveyAnswers) {
}
