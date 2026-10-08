-- Reference DDL for the transactional outbox (docs/architecture.md §4.3).
-- Copy into each service's per-schema Flyway migration as auth/V5__create_outbox_events.sql
-- (and the equivalent for the other schemas). No cross-schema access:
-- the table lives in the owning service's schema and is used only by it.
--
-- Column layout mirrors the EventEnvelope wire contract
-- {event_id, type, version, occurred_at, producer, correlation_id, payload}.

CREATE TABLE outbox_events (
    id             BIGSERIAL   PRIMARY KEY,
    event_id       TEXT        UNIQUE NOT NULL,
    event_type     TEXT        NOT NULL,
    version        INT         NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    producer       TEXT        NOT NULL,
    correlation_id TEXT        NULL,
    payload        JSONB       NOT NULL,
    published_at   TIMESTAMPTZ NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index: the relay only ever scans unpublished rows.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
