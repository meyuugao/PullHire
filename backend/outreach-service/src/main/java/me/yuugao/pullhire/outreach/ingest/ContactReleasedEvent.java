package me.yuugao.pullhire.outreach.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code candidate.contact_released} event envelope as published by
 * candidate-service ({@code docs/architecture.md §4.2}). Field names mirror
 * the wire JSON (snake_case).
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
public record ContactReleasedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "candidate.contact_released";

    /** Payload ({@code contracts/events/candidate.contact_released.schema.json}). */
    public record Payload(
            @JsonProperty("candidate_id") String candidateId,
            @JsonProperty("contact_email") String contactEmail,
            @JsonProperty("contact_phone") String contactPhone,
            @JsonProperty("released_at") String releasedAt) {
    }
}
