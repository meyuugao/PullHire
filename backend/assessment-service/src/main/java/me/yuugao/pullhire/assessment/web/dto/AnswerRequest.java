package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * {@code POST /assessment/sessions/{id}/answers} body ({@code docs/api.md §3.4}).
 *
 * <p>Wire shape follows {@code docs/api.md §1}: {@code snake_case} keys and
 * stringified identifiers (like {@code SurveyRequest}/{@code SessionStartResponse}).
 * The candidate id is taken from the JWT, never from the body; the answered
 * item is addressed by its stringified {@code item_bank.id}.
 *
 * <p>{@code proctoring_flags}, when present, carries the client-observed
 * signals ({@code visibilitychange} / {@code fullscreen_exit} / {@code paste},
 * {@code docs/testing_model.md §9.2}) persisted into
 * {@code attempts.proctoring_flags} (jsonb) alongside the answer.
 */
public record AnswerRequest(
        @JsonProperty("item_id") @NotBlank String itemId,
        @NotNull JsonNode answer,
        @JsonProperty("response_ms") Integer responseMs,
        @JsonProperty("proctoring_flags") JsonNode proctoringFlags) {
}
