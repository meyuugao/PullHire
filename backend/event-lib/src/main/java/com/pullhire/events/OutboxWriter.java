package com.pullhire.events;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Transactional-outbox writer ({@code docs/architecture.md §4.3}). Inserts one
 * row into the service's own {@code outbox_events} table using that service's
 * {@link JdbcTemplate}, so the insert participates in the ambient transaction:
 * the event is committed atomically with the business change, or not at all.
 *
 * <p>Reference DDL: {@code com/pullhire/events/outbox-events.reference.sql}.
 * This class never talks to the broker — delivery is {@link OutboxRelay}'s job.
 */
public class OutboxWriter {

    private static final String INSERT = "INSERT INTO outbox_events "
            + "(event_id, event_type, version, occurred_at, producer, correlation_id, payload) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbc;

    public OutboxWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Persists the envelope in the current transaction. */
    public void write(EventEnvelope envelope) {
        jdbc.update(INSERT,
                envelope.eventId(),
                envelope.type(),
                envelope.version(),
                OffsetDateTime.ofInstant(envelope.occurredAt(), ZoneOffset.UTC),
                envelope.producer(),
                envelope.correlationId(),
                envelope.payloadJson());
    }
}
