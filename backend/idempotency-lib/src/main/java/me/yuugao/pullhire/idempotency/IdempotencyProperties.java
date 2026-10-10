package me.yuugao.pullhire.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Idempotency-Key middleware tuning ({@code pullhire.idempotency.*}); bound in
 * every service that uses idempotency-lib (docs/api.md §1–§2).
 */
@ConfigurationProperties(prefix = "pullhire.idempotency")
public class IdempotencyProperties {

    /**
     * Enables the {@link IdempotencyFilter}. Defaults to {@code false}:
     * consumers opt in explicitly ({@code pullhire.idempotency.enabled: true}).
     */
    private boolean enabled = false;

    /** Ant path patterns the filter applies to; empty means all POST requests. */
    private List<String> pathPatterns = List.of();

    /** TTL for stored results; expired rows are cleaned lazily on insert. */
    private Duration ttl = Duration.ofHours(24);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
