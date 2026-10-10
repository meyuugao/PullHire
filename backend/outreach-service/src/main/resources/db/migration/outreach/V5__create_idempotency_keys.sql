-- outreach.idempotency_keys (docs/api.md §1, decision C): Idempotency-Key
-- result store for POST /invitations creation. One row per key, holding the
-- successful (2xx) response replayed on repeats. Error responses are never
-- cached.
CREATE TABLE idempotency_keys (
    id              BIGSERIAL   PRIMARY KEY,
    idempotency_key VARCHAR     NOT NULL UNIQUE,
    status_code     INT         NOT NULL,
    content_type    VARCHAR     NULL,
    response_body   BYTEA       NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
