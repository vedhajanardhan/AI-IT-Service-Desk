-- Core schema: users + incident management.
-- Later phases (AI analysis, remediation, knowledge base, notifications,
-- audit log) add their own tables in subsequent V__ migrations so each
-- phase's schema change is independently reviewable.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    department      VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE incidents (
    id                      UUID PRIMARY KEY,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT         NOT NULL,
    category                VARCHAR(30)  NOT NULL,
    severity                VARCHAR(20)  NOT NULL,
    priority                VARCHAR(20),
    status                  VARCHAR(30)  NOT NULL,
    reporter_id             UUID         NOT NULL REFERENCES users (id),
    assigned_engineer_id    UUID         REFERENCES users (id),
    resolution_details      TEXT,
    resolved_at             TIMESTAMPTZ,
    escalated_at            TIMESTAMPTZ,
    escalation_reason       TEXT,
    remediation_attempts    INTEGER      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_incidents_status ON incidents (status);
CREATE INDEX idx_incidents_reporter ON incidents (reporter_id);
CREATE INDEX idx_incidents_assigned_engineer ON incidents (assigned_engineer_id);
CREATE INDEX idx_incidents_created_at ON incidents (created_at);
CREATE INDEX idx_incidents_category ON incidents (category);
CREATE INDEX idx_incidents_severity ON incidents (severity);

CREATE TABLE incident_comments (
    id              UUID PRIMARY KEY,
    incident_id     UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    author_id       UUID        NOT NULL REFERENCES users (id),
    body            TEXT        NOT NULL,
    internal_only   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_incident_comments_incident ON incident_comments (incident_id);

CREATE TABLE incident_events (
    id              UUID PRIMARY KEY,
    incident_id     UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    event_type      VARCHAR(40) NOT NULL,
    description     TEXT,
    actor_id        UUID        REFERENCES users (id),
    previous_status VARCHAR(30),
    new_status      VARCHAR(30),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_incident_events_incident ON incident_events (incident_id);
CREATE INDEX idx_incident_events_created_at ON incident_events (created_at);
