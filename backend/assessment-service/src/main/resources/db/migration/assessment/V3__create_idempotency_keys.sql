-- Persistent Idempotency-Key result store for assessment-service (docs/api.md §1, decision B: answers only).
-- Mirrors backend/idempotency-lib/src/main/resources/me/yuugao/pullhire/idempotency/idempotency-keys.reference.sql.
-- Lives in the `assessment` schema only; no cross-schema access (docs/architecture.md §5.2).

CREATE TABLE idempotency_keys (
    id              BIGSERIAL   PRIMARY KEY,
    idempotency_key VARCHAR     NOT NULL UNIQUE,
    status_code     INT         NOT NULL,
    content_type    VARCHAR     NULL,
    response_body   BYTEA       NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
