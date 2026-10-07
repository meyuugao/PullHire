-- auth.consents (docs/data_model.md §3.4). Append-only 152-FZ consent journal.
-- No UNIQUE constraint by design: the current state is the latest row per
-- (user_id, consent_type); every change appends a new row (docs/architecture.md §6.2).
CREATE TABLE consents (
    id             bigserial   PRIMARY KEY,
    user_id        bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_type   text        NOT NULL CHECK (consent_type IN ('data_processing', 'profile_publication', 'fsp_link', 'task_participation')),
    granted        boolean     NOT NULL,
    policy_version text        NOT NULL,
    granted_at     timestamptz NULL,
    revoked_at     timestamptz NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_consents_user_type_created ON consents (user_id, consent_type, created_at);
