package com.pullhire.events;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Broker-free contract tests for the wire envelope: exact snake_case keys,
 * {@code version=1}, and a lossless JSON round-trip.
 */
class EventEnvelopeTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static EventEnvelope sample() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", "42");
        payload.put("email", "candidate@example.com");
        payload.put("role", "candidate");
        payload.put("verification_url", "https://app.pullhire.local/verify-email?token=abc");
        return EventEnvelope.of(EventNames.USER_REGISTERED, "auth-service", "req-123", payload);
    }

    @Test
    void factoryGeneratesIdentityAndVersionOne() {
        EventEnvelope envelope = sample();

        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.type()).isEqualTo("user.registered");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(envelope.producer()).isEqualTo("auth-service");
        assertThat(envelope.correlationId()).isEqualTo("req-123");
    }

    @Test
    void jsonUsesExactSnakeCaseFieldNames() {
        Map<String, Object> json = JSON.readValue(sample().toJson(), Map.class);

        assertThat(json).containsOnlyKeys(
                "event_id", "type", "version", "occurred_at", "producer", "correlation_id", "payload");
        assertThat(json.get("version")).isEqualTo(1);
        assertThat(json.get("type")).isEqualTo("user.registered");
        assertThat(json.get("correlation_id")).isEqualTo("req-123");
        assertThat(json.get("payload")).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) json.get("payload"))
                .containsEntry("user_id", "42")
                .containsEntry("verification_url", "https://app.pullhire.local/verify-email?token=abc")
                .doesNotContainKey("verification_token");
    }

    @Test
    void jsonRoundTripIsLossless() {
        EventEnvelope original = sample();

        EventEnvelope parsed = EventEnvelope.fromJson(original.toJson());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void payloadJsonSerializesOnlyThePayload() {
        Map<String, Object> payload = JSON.readValue(sample().payloadJson(), Map.class);

        assertThat(payload).containsOnlyKeys("user_id", "email", "role", "verification_url");
    }
}
