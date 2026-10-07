package com.pullhire.auth.application;

import com.pullhire.auth.domain.Role;
import com.pullhire.events.EventEnvelope;
import com.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * E1 (blocker): machine-checks that the payload actually built by
 * {@link EventPublisher} conforms to the committed
 * {@code contracts/events/user.registered.schema.json}.
 *
 * <p>The real builder runs (with a mocked transport), the produced envelope is
 * captured, its {@code payload} JSON is serialized and validated against the
 * committed schema. {@code additionalProperties:false} guards the OQ-13 rule
 * (no raw {@code verification_token} may leak) and required keys/types are
 * checked from the schema file itself.
 */
class UserRegisteredPayloadSchemaTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void builtPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema = readObject(contractsDir().resolve("user.registered.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EventPublisher publisher = new EventPublisher(outbox);

        publisher.publishUserRegistered(42L, "candidate@example.com", Role.CANDIDATE,
                "https://app.pullhire.local/verify-email?token=abc", "req-123");

        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        Map<String, Object> payloadJson = JSON.readValue(captured.getValue().payloadJson(), Map.class);

        assertThat(payloadJson).doesNotContainKey("verification_token");
        JsonSchema.validate("$", schema, payloadJson);
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
    private static Map<String, Object> readObject(Path path) throws Exception {
        return JSON.readValue(Files.readString(path), Map.class);
    }
}
