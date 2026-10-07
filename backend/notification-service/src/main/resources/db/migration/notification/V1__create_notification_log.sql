-- notification.notification_log (docs/data_model.md §10.1). Outbound email journal / audit.
-- recipient_user_id is a logical reference to auth.users.id — no cross-schema FK.
-- source_event_id is UNIQUE: the consumer's per-event idempotency guard.
CREATE TABLE notification_log (
    id                 bigserial   PRIMARY KEY,
    recipient_user_id  bigint      NULL,
    recipient_email    text        NOT NULL,
    template_code      text        NOT NULL,
    event_type         text        NOT NULL,
    source_event_id    text        NULL,
    status             text        NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'sent', 'failed')),
    provider_message_id text       NULL,
    error              text        NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    sent_at            timestamptz NULL,
    CONSTRAINT uq_notification_log_source_event_id UNIQUE (source_event_id)
);

CREATE INDEX idx_notification_log_recipient_user_id ON notification_log (recipient_user_id);
CREATE INDEX idx_notification_log_status ON notification_log (status);
CREATE INDEX idx_notification_log_created_at ON notification_log (created_at);
