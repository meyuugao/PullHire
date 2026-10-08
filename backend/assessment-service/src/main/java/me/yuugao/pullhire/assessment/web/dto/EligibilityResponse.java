package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.assessment.application.GradeEligibilityService.GradeEligibility;

import java.time.Instant;

/**
 * {@code GET /assessment/grade-eligibility} response ({@code docs/api.md §3.4}).
 *
 * <p>{@code eligible} is {@code false} exactly when an unexpired
 * {@code grade_change} cooldown blocks the change; in that case
 * {@code available_after} carries the instant the cooldown lifts and is
 * {@code null} when the candidate is eligible.
 */
public record EligibilityResponse(
        boolean eligible,
        @JsonProperty("available_after") Instant availableAfter) {

    public static EligibilityResponse from(GradeEligibility eligibility) {
        return new EligibilityResponse(eligibility.eligible(), eligibility.availableAfter());
    }
}
