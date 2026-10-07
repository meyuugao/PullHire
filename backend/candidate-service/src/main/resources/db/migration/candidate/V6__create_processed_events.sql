-- candidate.processed_events (docs/architecture.md §4.2): consumer-side idempotency
-- by event_id for candidate.invitation-accepted (OQ-22). The PRIMARY KEY is the
-- race-free guard; one row per claimed event.
CREATE TABLE processed_events (
    event_id     text        PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
