-- auth.email_tokens (docs/data_model.md §3.2). One-time email verification tokens.
-- Only the SHA-256 hash is stored; the raw token travels in the event payload.
CREATE TABLE email_tokens (
    id         bigserial   PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text        NOT NULL,
    purpose    text        NOT NULL DEFAULT 'verify_email' CHECK (purpose IN ('verify_email')),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_tokens_user_id ON email_tokens (user_id);
CREATE INDEX idx_email_tokens_expires_at ON email_tokens (expires_at);
