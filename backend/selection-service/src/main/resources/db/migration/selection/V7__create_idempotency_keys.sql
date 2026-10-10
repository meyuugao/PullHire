-- selection.idempotency_keys (docs/api.md §1, assumptions.md §12). Result store
-- for the Idempotency-Key middleware on POST /selections: one row per key,
-- holding the successful (2xx) response replayed on repeats.
-- Mirrors backend/idempotency-lib/src/main/resources/me/yuugao/pullhire/idempotency/idempotency-keys.reference.sql.
-- No schema prefix: Flyway default-schema=selection.
CREATE TABLE idempotency_keys (
    id              BIGSERIAL   PRIMARY KEY,
    idempotency_key VARCHAR     NOT NULL UNIQUE,
    status_code     INT         NOT NULL,
    content_type    VARCHAR     NULL,
    response_body   BYTEA       NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
