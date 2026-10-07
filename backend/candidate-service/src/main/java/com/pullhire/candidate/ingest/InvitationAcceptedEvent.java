package com.pullhire.candidate.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code invitation.accepted} event envelope as published by outreach-service
 * (S8) ({@code docs/architecture.md §4.2}). Field names mirror the wire JSON
 * (snake_case); unknown future fields are ignored by Jackson.
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
public record InvitationAcceptedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "invitation.accepted";

    /** Payload ({@code contracts/events/invitation.accepted.schema.json}); {@code candidate_id}
     * is {@code candidate.profiles.id}. */
    public record Payload(
            @JsonProperty("invitation_id") String invitationId,
            @JsonProperty("candidate_id") String candidateId,
            @JsonProperty("employer_id") String employerId) {
    }
}
