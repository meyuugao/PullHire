package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /assessment/survey} response ({@code docs/api.md §3.4}): the id of
 * the freshly opened session that now carries the persisted survey stage
 * ({@code docs/backlog/closed_questions.md TD-3}).
 */
public record SurveyResponse(@JsonProperty("session_id") String sessionId) {

    public static SurveyResponse from(long sessionId) {
        return new SurveyResponse(Long.toString(sessionId));
    }
}
