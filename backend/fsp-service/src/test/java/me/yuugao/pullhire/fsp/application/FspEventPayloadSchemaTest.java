package me.yuugao.pullhire.fsp.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.fsp.adapter.RegistryAchievement;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Machine-checks that the {@code fsp.linked} and {@code fsp.achievements_updated}
 * payloads built by {@link EventPublisher} conform to the committed contract schemas
 * and travel in the S2 envelope ({@code contracts/events/envelope.schema.json}).
 */
class FspEventPayloadSchemaTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void linkedPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema = Contracts.readObject(Contracts.dir().resolve("fsp.linked.schema.json"));
        Map<String, Object> envelopeSchema = Contracts.readObject(Contracts.dir().resolve("envelope.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox).publishLinked(7L, "fsp-1", LinkStatus.LINKED, HistoryStatus.PRESENT);

        EventEnvelope envelope = capture(outbox);
        assertThat(envelope.version()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.type()).isEqualTo(EventNames.FSP_LINKED);
        assertThat(envelope.producer()).isEqualTo("fsp-service");
        JsonSchema.validate("$", schema, payloadOf(envelope));
        JsonSchema.validate("$", envelopeSchema, envelopeMap(envelope));
    }

    @Test
    void achievementsUpdatedPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("fsp.achievements_updated.schema.json"));
        Map<String, Object> envelopeSchema = Contracts.readObject(Contracts.dir().resolve("envelope.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        List<RegistryAchievement> achievements = List.of(
                new RegistryAchievement("a-1", "Победа", "championship", LocalDate.of(2023, 5, 14), 1, null),
                new RegistryAchievement("a-2", "Участие", "selection", null, null, null));
        new EventPublisher(outbox)
                .publishAchievementsUpdated(7L, "fsp-1", HistoryStatus.PRESENT, achievements);

        EventEnvelope envelope = capture(outbox);
        assertThat(envelope.version()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.type()).isEqualTo(EventNames.FSP_ACHIEVEMENTS_UPDATED);
        JsonSchema.validate("$", schema, payloadOf(envelope));
        JsonSchema.validate("$", envelopeSchema, envelopeMap(envelope));
    }

    @Test
    void emptyHistoryPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("fsp.achievements_updated.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox)
                .publishAchievementsUpdated(7L, "fsp-1", HistoryStatus.NO_HISTORY, List.of());

        JsonSchema.validate("$", schema, payloadOf(capture(outbox)));
    }

    private static EventEnvelope capture(OutboxEventPublisher outbox) {
        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        return captured.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(EventEnvelope envelope) {
        return JSON.readValue(envelope.payloadJson(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeMap(EventEnvelope envelope) {
        return JSON.readValue(envelope.toJson(), Map.class);
    }
}
