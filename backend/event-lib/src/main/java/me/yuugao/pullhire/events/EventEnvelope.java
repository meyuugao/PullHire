package me.yuugao.pullhire.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The shared event envelope ({@code docs/architecture.md §4.2}):
 * {@code {event_id, type, version, occurred_at, producer, correlation_id, payload}}.
 *
 * <p>JSON field names are the exact snake_case wire contract and are asserted by
 * unit tests; records serialize components in declaration order, so the on-wire
 * key order matches the documentation. {@code version} starts at {@link #VERSION}
 * (1) and is bumped only on an incompatible payload change.
 *
 * <p>Plain Jackson 3 ({@code tools.jackson}) — no Spring context is required,
 * so this type is usable from tests and non-Spring code alike.
 */
public record EventEnvelope(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") int version,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Map<String, Object> payload) {

    /** Envelope schema version; increment only on an incompatible payload change. */
    public static final int VERSION = 1;

    private static final JsonMapper JSON = new JsonMapper();

    /**
     * Builds a fresh envelope: new {@code event_id} (UUID), {@code version=1},
     * {@code occurred_at=Instant.now()}.
     */
    public static EventEnvelope of(String type, String producer, String correlationId,
                                   Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), type, VERSION, Instant.now(),
                producer, correlationId, payload);
    }

    /** Serializes the whole envelope to its wire JSON. */
    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    /** Serializes only the {@code payload} object (for the outbox {@code jsonb} column). */
    public String payloadJson() {
        return JSON.writeValueAsString(payload);
    }

    /** Parses an envelope from its wire JSON. */
    public static EventEnvelope fromJson(String json) {
        return JSON.readValue(json, EventEnvelope.class);
    }
}
