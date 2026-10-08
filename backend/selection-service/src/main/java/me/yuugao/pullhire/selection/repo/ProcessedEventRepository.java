package me.yuugao.pullhire.selection.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consumer-side dedup for {@code selection.processed_events}
 * ({@code docs/architecture.md §4.2}). The {@code PRIMARY KEY} on
 * {@code source_event_id} is the race-free guard; it stores the envelope
 * {@code event_id} of the source event.
 */
@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbc;

    public ProcessedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically claims a source {@code event_id}; the insert-claim is race-free
     * (unlike a read-then-insert check).
     *
     * @return {@code true} when this delivery claimed the event, {@code false} when it
     *         was already processed (no row inserted).
     */
    public boolean claim(String eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (source_event_id) VALUES (?) ON CONFLICT (source_event_id) DO NOTHING",
                eventId);
        return inserted > 0;
    }
}
