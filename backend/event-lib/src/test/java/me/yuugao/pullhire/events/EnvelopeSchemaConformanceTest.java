package me.yuugao.pullhire.events;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.fail;

/**
 * E1 (blocker): machine-checks that a real serialized {@link EventEnvelope}
 * conforms to the committed {@code contracts/events/envelope.schema.json}.
 *
 * <p>No schema-validator dependency: the committed JSON Schema is parsed with
 * Jackson 3 and interpreted by a small subset evaluator covering exactly the
 * keywords the PullHire schemas use ({@code type}, {@code required},
 * {@code properties}, {@code additionalProperties}, {@code const}, {@code enum},
 * {@code minLength}, {@code format}, arrays-of-types). {@code additionalProperties:false}
 * is what catches a stray envelope key; {@code const} pins {@code version=1}.
 */
class EnvelopeSchemaConformanceTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void serializedEnvelopeConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema = readObject(contractsDir().resolve("envelope.schema.json"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", "42");
        payload.put("email", "candidate@example.com");
        payload.put("role", "candidate");
        payload.put("verification_url", "https://app.pullhire.local/verify-email?token=abc");
        EventEnvelope envelope =
                EventEnvelope.of(EventNames.USER_REGISTERED, "auth-service", "req-123", payload);

        JsonSchema.validate("$", schema, JSON.readValue(envelope.toJson(), Object.class));
    }

    @Test
    void nullCorrelationIdStillConforms() throws Exception {
        Map<String, Object> schema = readObject(contractsDir().resolve("envelope.schema.json"));

        EventEnvelope envelope = EventEnvelope.of(EventNames.USER_REGISTERED, "auth-service", null, Map.of());

        JsonSchema.validate("$", schema, JSON.readValue(envelope.toJson(), Object.class));
    }

    /** Resolves {@code <repo>/contracts/events} via the Gradle-provided system property, else by walking up. */
    static Path contractsDir() {
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
    static Map<String, Object> readObject(Path path) throws Exception {
        return JSON.readValue(Files.readString(path), Map.class);
    }
}
