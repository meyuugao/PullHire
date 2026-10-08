package me.yuugao.pullhire.selection.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code candidate.card_updated} event envelope as published by candidate-service
 * ({@code docs/architecture.md §6.2}). Field names mirror the wire JSON
 * (snake_case); unknown future fields are ignored by Jackson.
 *
 * <p>Payload carries only the agreed search-projection fields
 * ({@code contracts/events/candidate.card_updated.schema.json},
 * {@code docs/data_model.md §7.3}) and MUST NOT contain PII. {@code display_name}
 * arrives already masked by the publisher — stored as-is.
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardUpdatedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "candidate.card_updated";

    /** Payload ({@code contracts/events/candidate.card_updated.schema.json});
     * {@code candidate_id} is {@code candidate.profiles.id} (logical reference). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("candidate_id") String candidateId,
            @JsonProperty("visible_to_employers") Boolean visibleToEmployers,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("headline") String headline,
            @JsonProperty("industry_code") String industryCode,
            @JsonProperty("specialization_code") String specializationCode,
            @JsonProperty("grade") String grade,
            @JsonProperty("stack") List<String> stack,
            @JsonProperty("fsp_verified") Boolean fspVerified,
            @JsonProperty("contact_released") Boolean contactReleased) {
    }
}
