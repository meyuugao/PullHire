package me.yuugao.pullhire.selection.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code assessment.category_assigned} event envelope as published by
 * assessment-service (TD-1) ({@code docs/architecture.md §3.1 row 4, §4.3}).
 * Field names mirror the wire JSON (snake_case); unknown future fields are
 * ignored by Jackson.
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
public record CategoryAssignedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "assessment.category_assigned";

    /** Payload ({@code contracts/events/assessment.category_assigned.schema.json});
     * {@code candidate_id} is {@code candidate.profiles.id} (logical reference).
     * {@code theta} is deliberately absent — it is never projected here. */
    public record Payload(
            @JsonProperty("candidate_id") String candidateId,
            @JsonProperty("specialization_code") String specializationCode,
            @JsonProperty("grade") String grade,
            @JsonProperty("category_code") String categoryCode,
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("assigned_at") String assignedAt) {
    }
}
