package me.yuugao.pullhire.employer.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Machine-checks that the {@code employer.need_deleted} payload built by
 * {@link EventPublisher} conforms to the committed
 * {@code contracts/events/employer.need_deleted.schema.json} and travels in
 * the S2 envelope ({@code contracts/events/envelope.schema.json}).
 *
 * <p>The real builder runs (with a mocked transport), the produced envelope is
 * captured, its {@code payload} JSON is serialized and validated against the
 * committed schema. Identifiers are strings ({@code docs/api.md §1}).
 */
class NeedDeletedPayloadSchemaTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void needDeletedPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema = readObject(contractsDir().resolve("employer.need_deleted.schema.json"));
        Map<String, Object> envelopeSchema = readObject(contractsDir().resolve("envelope.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox).publishNeedDeleted("11", "7", "1");

        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        EventEnvelope envelope = captured.getValue();

        assertThat(envelope.version()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.type()).isEqualTo(EventNames.EMPLOYER_NEED_DELETED);
        assertThat(envelope.producer()).isEqualTo("employer-service");
        JsonSchema.validate("$", schema, payloadOf(envelope));
        JsonSchema.validate("$", envelopeSchema, envelopeMap(envelope));
    }

    /** Resolves {@code <repo>/contracts/events} via the Gradle-provided system property, else by walking up. */
    private static Path contractsDir() {
        String property = System.getProperty("pullhire.contracts.dir");
        if (property != null && Files.isDirectory(Path.of(property))) {
            return Path.of(property);
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("contracts").resolve("events");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "contracts/events not found; run via Gradle or set -Dpullhire.contracts.dir");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(EventEnvelope envelope) {
        return JSON.readValue(envelope.payloadJson(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeMap(EventEnvelope envelope) {
        return JSON.readValue(envelope.toJson(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readObject(Path path) throws Exception {
        return JSON.readValue(Files.readString(path), Map.class);
    }
}
