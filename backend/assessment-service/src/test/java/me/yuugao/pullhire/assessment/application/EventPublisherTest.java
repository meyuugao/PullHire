package me.yuugao.pullhire.assessment.application;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.events.CorrelationMdcFilter;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Machine-checks that the {@code assessment.category_assigned} and
 * {@code assessment.grade_changed} payloads built by {@link EventPublisher} conform to
 * the committed contract schemas and travel in the S2 envelope
 * ({@code contracts/events/envelope.schema.json}).
 *
 * <p>Plain JUnit 5 + AssertJ + Mockito. The schema {@link Contracts} locator and the
 * {@link JsonSchema} evaluator are nested here because this module's test classpath is
 * not shared with the other services' copies of the same helpers.
 */
class EventPublisherTest {

    private static final JsonMapper JSON = new JsonMapper();
    private static final Instant ASSIGNED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void categoryAssignedPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("assessment.category_assigned.schema.json"));
        Map<String, Object> envelopeSchema = Contracts.readObject(Contracts.dir().resolve("envelope.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox)
                .publishCategoryAssigned(7L, "backend", "B", "backend-B", "s-1", ASSIGNED_AT);

        EventEnvelope envelope = capture(outbox);
        assertThat(envelope.version()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.type()).isEqualTo(EventNames.ASSESSMENT_CATEGORY_ASSIGNED);
        assertThat(envelope.producer()).isEqualTo("assessment-service");
        JsonSchema.validate("$", schema, payloadOf(envelope));
        JsonSchema.validate("$", envelopeSchema, envelopeMap(envelope));
    }

    @Test
    void categoryAssignedNullSessionConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("assessment.category_assigned.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox)
                .publishCategoryAssigned(7L, "backend", "B", "backend-B", null, ASSIGNED_AT);

        Map<String, Object> payload = payloadOf(capture(outbox));
        assertThat(payload).containsEntry("session_id", null);
        assertThat(payload.get("assigned_at")).isInstanceOf(String.class);
        Instant.parse(String.valueOf(payload.get("assigned_at")));
        JsonSchema.validate("$", schema, payload);
    }

    @Test
    void categoryAssignedNullAssignedAtDefaultsToNow() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("assessment.category_assigned.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox)
                .publishCategoryAssigned(7L, "backend", "B", "backend-B", null, null);

        Map<String, Object> payload = payloadOf(capture(outbox));
        assertThat(payload).containsEntry("session_id", null);
        assertThat(payload.get("assigned_at")).isInstanceOf(String.class);
        Instant.parse(String.valueOf(payload.get("assigned_at")));
        JsonSchema.validate("$", schema, payload);
    }

    @Test
    void gradeChangedPayloadConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("assessment.grade_changed.schema.json"));
        Map<String, Object> envelopeSchema = Contracts.readObject(Contracts.dir().resolve("envelope.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox).publishGradeChanged(7L, "backend", "A", GradeDirection.UP,
                "backend-A", "s-2", "recheck raised the band");

        EventEnvelope envelope = capture(outbox);
        assertThat(envelope.version()).isEqualTo(EventEnvelope.VERSION);
        assertThat(envelope.type()).isEqualTo(EventNames.ASSESSMENT_GRADE_CHANGED);
        assertThat(envelope.producer()).isEqualTo("assessment-service");
        Map<String, Object> payload = payloadOf(envelope);
        assertThat(payload).containsEntry("direction", "up");
        JsonSchema.validate("$", schema, payload);
        JsonSchema.validate("$", envelopeSchema, envelopeMap(envelope));
    }

    @Test
    void gradeChangedNullSessionAndReasonConformsToCommittedSchema() throws Exception {
        Map<String, Object> schema =
                Contracts.readObject(Contracts.dir().resolve("assessment.grade_changed.schema.json"));

        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        new EventPublisher(outbox).publishGradeChanged(7L, "backend", "A", GradeDirection.INITIAL,
                null, null, null);

        Map<String, Object> payload = payloadOf(capture(outbox));
        assertThat(payload).containsEntry("session_id", null).containsEntry("reason", null)
                .containsEntry("category_code", null);
        JsonSchema.validate("$", schema, payload);
    }

    @Test
    void gradeChangedNullDirectionIsRejected() {
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);

        try {
            new EventPublisher(outbox).publishGradeChanged(7L, "backend", "A", null,
                    "backend-A", "s-2", null);
            fail("expected NullPointerException for null direction");
        } catch (NullPointerException expected) {
            assertThat(expected).hasMessageContaining("direction");
        }
    }

    @Test
    void correlationIdIsPropagatedFromMdc() {
        MDC.put(CorrelationMdcFilter.MDC_KEY, "req-123");
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);

        new EventPublisher(outbox)
                .publishCategoryAssigned(7L, "backend", "B", "backend-B", "s-1", ASSIGNED_AT);

        assertThat(capture(outbox).correlationId()).isEqualTo("req-123");
    }

    @Test
    void blankCorrelationIdIsNull() {
        MDC.put(CorrelationMdcFilter.MDC_KEY, "   ");
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);

        new EventPublisher(outbox).publishGradeChanged(7L, "backend", "A", GradeDirection.RECHECK,
                "backend-A", "s-2", null);

        assertThat(capture(outbox).correlationId()).isNull();
    }

    @Test
    void missingCorrelationIdIsNull() {
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);

        new EventPublisher(outbox)
                .publishCategoryAssigned(7L, "backend", "B", "backend-B", "s-1", ASSIGNED_AT);

        assertThat(capture(outbox).correlationId()).isNull();
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

    /** Locates and reads the committed event contract schemas for tests. */
    private static final class Contracts {

        private Contracts() {
        }

        /** Resolves {@code <repo>/contracts/events} via the Gradle system property, else by walking up. */
        static Path dir() {
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

    /**
     * Dependency-free evaluator for the subset of JSON Schema used by the committed
     * PullHire event contracts. Interprets {@code type} (incl. array-of-types),
     * {@code required}, {@code properties}, {@code additionalProperties}, {@code const},
     * {@code enum}, {@code minLength}, {@code minimum}, {@code items} and {@code format}.
     */
    private static final class JsonSchema {

        private JsonSchema() {
        }

        static void validate(String path, Map<String, Object> schema, Object value) {
            Object type = schema.get("type");
            if (type != null && !typeMatches(type, value)) {
                fail(path + ": expected type " + type + " but was " + jsonType(value));
            }
            if (schema.containsKey("const") && !valuesEqual(schema.get("const"), value)) {
                fail(path + ": expected const " + schema.get("const") + " but was " + value);
            }
            if (schema.get("enum") instanceof List<?> allowed
                    && allowed.stream().noneMatch(option -> valuesEqual(option, value))) {
                fail(path + ": value " + value + " not in enum " + allowed);
            }
            if (value instanceof String text) {
                if (schema.get("minLength") instanceof Number minLength
                        && text.length() < minLength.intValue()) {
                    fail(path + ": shorter than minLength " + minLength);
                }
                if (schema.get("format") instanceof String format) {
                    checkFormat(path, format, text);
                }
            }
            if (value instanceof Number number
                    && schema.get("minimum") instanceof Number minimum
                    && number.doubleValue() < minimum.doubleValue()) {
                fail(path + ": " + number + " is less than minimum " + minimum);
            }
            if (value instanceof Map<?, ?> object) {
                validateObject(path, schema, object);
            }
            if (value instanceof List<?> array && schema.get("items") instanceof Map<?, ?> items) {
                for (int i = 0; i < array.size(); i++) {
                    validate(path + "[" + i + "]", cast(items), array.get(i));
                }
            }
        }

        private static void validateObject(String path, Map<String, Object> schema, Map<?, ?> object) {
            if (schema.get("required") instanceof List<?> required) {
                for (Object name : required) {
                    if (!object.containsKey(name)) {
                        fail(path + ": missing required property '" + name + "'");
                    }
                }
            }
            Map<String, Object> properties =
                    schema.get("properties") instanceof Map<?, ?> declared ? cast(declared) : Map.of();
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (properties.get(key) instanceof Map<?, ?> propertySchema) {
                    validate(path + "." + key, cast(propertySchema), entry.getValue());
                } else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                    fail(path + ": additional property not allowed '" + key + "'");
                }
            }
        }

        private static boolean typeMatches(Object type, Object value) {
            if (type instanceof String single) {
                return matchesType(single, value);
            }
            if (type instanceof List<?> alternatives) {
                return alternatives.stream().anyMatch(option -> matchesType(String.valueOf(option), value));
            }
            return true;
        }

        private static boolean matchesType(String type, Object value) {
            return switch (type) {
                case "object" -> value instanceof Map;
                case "array" -> value instanceof List;
                case "string" -> value instanceof String;
                case "integer" -> value instanceof Integer || value instanceof Long
                        || value instanceof java.math.BigInteger;
                case "number" -> value instanceof Number;
                case "boolean" -> value instanceof Boolean;
                case "null" -> value == null;
                default -> true;
            };
        }

        private static void checkFormat(String path, String format, String value) {
            try {
                switch (format) {
                    case "uuid" -> UUID.fromString(value);
                    case "date-time" -> Instant.parse(value);
                    case "date" -> java.time.LocalDate.parse(value);
                    case "uri" -> new URI(value);
                    case "email" -> {
                        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                            fail(path + ": '" + value + "' is not a valid email");
                        }
                    }
                    default -> {
                        // Unknown formats are not asserted; schemas only use the ones above.
                    }
                }
            } catch (AssertionError error) {
                throw error;
            } catch (Exception ex) {
                fail(path + ": '" + value + "' is not a valid " + format);
            }
        }

        private static boolean valuesEqual(Object left, Object right) {
            if (left instanceof Number a && right instanceof Number b) {
                return a.doubleValue() == b.doubleValue();
            }
            return Objects.equals(left, right);
        }

        private static String jsonType(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Map) {
                return "object";
            }
            if (value instanceof List) {
                return "array";
            }
            if (value instanceof String) {
                return "string";
            }
            if (value instanceof Boolean) {
                return "boolean";
            }
            if (value instanceof Number) {
                return "number";
            }
            return value.getClass().getSimpleName();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> cast(Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
    }
}
