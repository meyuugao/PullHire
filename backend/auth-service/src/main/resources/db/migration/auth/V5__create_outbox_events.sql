-- Transactional outbox for auth-service (docs/architecture.md §4.3).
-- Mirrors backend/event-lib/src/main/resources/me/yuugao/pullhire/events/outbox-events.reference.sql.
-- Lives in the `auth` schema only; no cross-schema access (docs/architecture.md §5.2).

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

-- Partial index: the relay only scans unpublished rows.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
