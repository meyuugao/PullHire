package com.pullhire.events;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Minimal consumer-side dedup helper keyed by {@code event_id}
 * ({@code docs/architecture.md §4.2}: idempotency by {@code event_id}).
 *
 * <p><b>Intended usage.</b> Each consumer keeps its own table (or uses an
 * existing log table) with a {@code UNIQUE} column holding the source
 * {@code event_id} (see {@code data_model.md §13} / {@code source_event_id}).
 * At the start of handling, inside the same transaction that writes the effect,
 * call {@link #isDuplicate(String, String, String)}; if false, insert the row
 * carrying the {@code event_id}. The {@code UNIQUE} constraint is the real
 * race-free guard — a {@code DuplicateKeyException} on insert means another
 * delivery won.
 *
 * <p>{@code table} and {@code column} must be trusted compile-time constants,
 * never user input; identifiers are validated defensively regardless.
 */
public class EventIdDedup {

    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_.]*";

    private final JdbcTemplate jdbc;

    public EventIdDedup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when the given {@code event_id} was already recorded in the consumer table. */
    public boolean isDuplicate(String eventId, String table, String column) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        validateIdentifier(table);
        validateIdentifier(column);
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE " + column + " = ?)",
                Boolean.class, eventId);
        return Boolean.TRUE.equals(exists);
    }

    private static void validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches(IDENTIFIER)) {
            throw new IllegalArgumentException("Недопустимый SQL-идентификатор: " + identifier);
        }
    }
}
