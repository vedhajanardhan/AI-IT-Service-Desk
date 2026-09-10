CREATE TABLE notifications (
    id                      UUID PRIMARY KEY,
    recipient_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type                    VARCHAR(40) NOT NULL,
    message                 TEXT        NOT NULL,
    related_incident_id     UUID,
    is_read                 BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, is_read);
