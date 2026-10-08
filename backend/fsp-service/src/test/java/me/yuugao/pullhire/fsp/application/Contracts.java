package me.yuugao.pullhire.fsp.application;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Locates and reads the committed event contract schemas for tests. */
public final class Contracts {

    private static final JsonMapper JSON = new JsonMapper();

    private Contracts() {
    }

    /** Resolves {@code <repo>/contracts/events} via the Gradle system property, else by walking up. */
    public static Path dir() {
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
    public static Map<String, Object> readObject(Path path) throws Exception {
        return JSON.readValue(Files.readString(path), Map.class);
    }
}
