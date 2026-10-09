-- outreach.invitation_events (docs/data_model.md §9.2): append-only audit of
-- status transitions; audit §13. No UNIQUE (append-only).
CREATE TABLE invitation_events (
    id            bigserial   PRIMARY KEY,
    invitation_id bigint      NOT NULL
                              REFERENCES invitations (id) ON DELETE CASCADE,
    event_type    text        NOT NULL
                              CHECK (event_type IN ('sent', 'viewed', 'accepted', 'declined', 'expired', 'contact_released')),
    actor_role    text        NULL
                              CHECK (actor_role IN ('employer', 'candidate', 'system')),
    payload       jsonb       NULL,
    occurred_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_invitation_events_invitation_occurred
    ON invitation_events (invitation_id, occurred_at);
