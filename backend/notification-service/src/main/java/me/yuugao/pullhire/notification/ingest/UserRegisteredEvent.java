package me.yuugao.pullhire.notification.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code user.registered} event envelope as published by auth-service
 * ({@code docs/architecture.md §4.2}). Field names mirror the wire JSON
 * (snake_case); unknown future fields are ignored by Jackson.
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
public record UserRegisteredEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "user.registered";

    /** Event payload ({@code contracts/events/user.registered.schema.json}). OQ-13: the
     * producer renders the full {@code verification_url}; the raw token never travels. */
    public record Payload(
            @JsonProperty("user_id") String userId,
            @JsonProperty("email") String email,
            @JsonProperty("role") String role,
            @JsonProperty("verification_url") String verificationUrl) {
    }
}
