-- auth.users (docs/data_model.md §3.1). Source of the JWT `sub` claim.
-- Role is fixed at registration (immutable) — enforced by application logic + no update path.
CREATE TABLE users (
    id                bigserial   PRIMARY KEY,
    email             text        NOT NULL,
    password_hash     text        NOT NULL,
    role              text        NOT NULL CHECK (role IN ('candidate', 'employer')),
    status            text        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'blocked')),
    email_verified_at timestamptz NULL,
    deleted_at        timestamptz NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_deleted_at ON users (deleted_at);
