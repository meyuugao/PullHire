package com.pullhire.candidate.application;

import com.pullhire.events.EventEnvelope;
import com.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Machine-checks that the {@code candidate.contact_released} payload built by
 * {@link EventPublisher} conforms to the committed
 * {@code contracts/events/candidate.contact_released.schema.json}.
 */
class ContactReleasedPayloadSchemaTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void builtPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("candidate.contact_released.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EventPublisher publisher = new EventPublisher(outbox);

        publisher.publishContactReleased("7", "candidate@example.com", "+7 999 123-45-67",
                Instant.parse("2026-10-07T10:00:00Z"));

        JsonSchema.validate("$", schema, capturePayload(outbox));
    }

    @Test
    void nullContactsPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("candidate.contact_released.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EventPublisher publisher = new EventPublisher(outbox);

        publisher.publishContactReleased("7", null, null, Instant.parse("2026-10-07T10:00:00Z"));

        JsonSchema.validate("$", schema, capturePayload(outbox));
    }

    private static Map<String, Object> capturePayload(OutboxEventPublisher outbox) {
        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        return JSON.readValue(captured.getValue().payloadJson(), Map.class);
    }
}
