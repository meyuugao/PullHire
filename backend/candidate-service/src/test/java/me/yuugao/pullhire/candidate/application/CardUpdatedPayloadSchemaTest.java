package me.yuugao.pullhire.candidate.application;

import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Machine-checks that the {@code candidate.card_updated} payload built by
 * {@link EventPublisher} conforms to the committed
 * {@code contracts/events/candidate.card_updated.schema.json}. The
 * {@code additionalProperties:false} contract guards the no-PII rule
 * ({@code docs/data_model.md §12}).
 */
class CardUpdatedPayloadSchemaTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void builtPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("candidate.card_updated.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EventPublisher publisher = new EventPublisher(outbox);

        publisher.publishCardUpdated("7", true, "Ivan P.", "Senior Java Developer", "it", "backend",
                "senior", List.of("Java", "Spring"), false, false);

        Map<String, Object> payload = capturePayload(outbox);
        assertThat(payload).doesNotContainKeys("email", "contact_email", "contact_phone", "phone", "full_name");
        JsonSchema.validate("$", schema, payload);
    }

    @Test
    void nullContentPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("candidate.card_updated.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EventPublisher publisher = new EventPublisher(outbox);

        publisher.publishCardUpdated("7", false, null, null, null, null, null, List.of(), false, false);

        JsonSchema.validate("$", schema, capturePayload(outbox));
    }

    private static Map<String, Object> capturePayload(OutboxEventPublisher outbox) {
        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        return JSON.readValue(captured.getValue().payloadJson(), Map.class);
    }
}
