package me.yuugao.pullhire.auth.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.fail;

/**
 * Dependency-free evaluator for the subset of JSON Schema used by the committed
 * PullHire event contracts (copy of the helper in event-lib's test sources; the
 * two modules' test classpaths are not shared). Interprets {@code type}
 * (incl. array-of-types), {@code required}, {@code properties},
 * {@code additionalProperties}, {@code const}, {@code enum}, {@code minLength},
 * {@code items} and {@code format}.
 *
 * <p>Test-only helper; not shipped.
 */
final class JsonSchema {

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
