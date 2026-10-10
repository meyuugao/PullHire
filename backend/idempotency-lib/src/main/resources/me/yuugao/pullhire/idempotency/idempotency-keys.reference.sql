-- Reference DDL for the Idempotency-Key result store (docs/api.md §1).
-- Copy into each service's per-schema Flyway migration as <service>/V<n>__create_idempotency_keys.sql
-- (and the equivalent for the other schemas). No cross-schema access:
-- the table lives in the owning service's schema and is used only by it.
--
-- Column layout: one row per Idempotency-Key, holding the successful (2xx)
-- response replayed on repeats. Error responses are never cached.

CREATE TABLE idempotency_keys (
    id              BIGSERIAL   PRIMARY KEY,
    idempotency_key VARCHAR     NOT NULL UNIQUE,
    status_code     INT         NOT NULL,
    content_type    VARCHAR     NULL,
    response_body   BYTEA       NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
