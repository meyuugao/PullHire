-- selection.processed_events (docs/architecture.md §4.2): consumer-side idempotency
-- by source_event_id. The PRIMARY KEY is the race-free guard; one row per
-- claimed event.
CREATE TABLE processed_events (
    source_event_id text        PRIMARY KEY,
    processed_at    timestamptz NOT NULL DEFAULT now()
);
