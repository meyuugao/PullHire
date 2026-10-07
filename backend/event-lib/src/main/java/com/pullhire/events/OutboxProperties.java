package com.pullhire.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbox relay tuning ({@code pullhire.outbox.*}); bound in every service that
 * uses event-lib. Defaults are safe for the demo footprint.
 */
@ConfigurationProperties(prefix = "pullhire.outbox")
public class OutboxProperties {

    /**
     * Enables the relay poller (and {@code @EnableScheduling}). Defaults to
     * {@code false}: the relay requires an {@code outbox_events} table, which
     * only publishing services have — consumer-only services must not get a
     * recurring scheduler that fails on every poll. Publishers opt in
     * explicitly ({@code pullhire.outbox.enabled: true}).
     */
    private boolean enabled = false;

    /** Max unpublished rows fetched per poll. */
    private int batchSize = 100;

    /** Delay between polls; simple Duration form, e.g. {@code 5s}. */
    private Duration interval = Duration.ofSeconds(5);

    /** How long the relay waits for a publisher confirm per message. */
    private Duration confirmTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public Duration getConfirmTimeout() {
        return confirmTimeout;
    }

    public void setConfirmTimeout(Duration confirmTimeout) {
        this.confirmTimeout = confirmTimeout;
    }
}
