CREATE TABLE remediation_actions (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    description         TEXT         NOT NULL,
    risk_level          VARCHAR(20)  NOT NULL,
    required_role       VARCHAR(20)  NOT NULL,
    approval_required   BOOLEAN      NOT NULL DEFAULT TRUE,
    timeout_seconds     INTEGER      NOT NULL DEFAULT 30,
    retry_limit         INTEGER      NOT NULL DEFAULT 2,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE remediation_executions (
    id                      UUID PRIMARY KEY,
    incident_id             UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    remediation_action_id   UUID        NOT NULL REFERENCES remediation_actions (id),
    status                  VARCHAR(30) NOT NULL,
    requested_by_id         UUID        NOT NULL REFERENCES users (id),
    approved_by_id          UUID        REFERENCES users (id),
    attempt_number          INTEGER     NOT NULL DEFAULT 1,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    failure_reason          TEXT,
    health_check_passed     BOOLEAN,
    idempotency_key         VARCHAR(150) NOT NULL UNIQUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_remediation_executions_incident ON remediation_executions (incident_id);
CREATE INDEX idx_remediation_executions_status ON remediation_executions (status);

-- Seed the fixed catalog of safe remediation actions. This IS the safety
-- boundary described in RemediationExecutor - nothing outside these six
-- rows can ever be executed.
INSERT INTO remediation_actions
    (id, code, name, description, risk_level, required_role, approval_required, timeout_seconds, retry_limit, enabled)
VALUES
    (gen_random_uuid(), 'RESTART_APPLICATION_SERVICE', 'Restart Application Service',
     'Gracefully restarts the affected application service instance to clear stuck threads, leaked connections, or corrupted in-memory state.',
     'MEDIUM', 'ENGINEER', TRUE, 60, 2, TRUE),

    (gen_random_uuid(), 'CLEAR_APPLICATION_CACHE', 'Clear Application Cache',
     'Clears the application-level in-memory cache to force fresh reads, resolving issues caused by stale cached data.',
     'LOW', 'ENGINEER', FALSE, 15, 2, TRUE),

    (gen_random_uuid(), 'INVALIDATE_REDIS_CACHE', 'Invalidate Redis Cache',
     'Invalidates affected Redis keys/namespaces to resolve stale-cache or cache-corruption issues without a full service restart.',
     'LOW', 'ENGINEER', FALSE, 15, 2, TRUE),

    (gen_random_uuid(), 'RESTART_CONNECTION_POOL', 'Restart Database Connection Pool',
     'Recycles the database connection pool to release leaked or stuck connections without restarting the whole service.',
     'MEDIUM', 'ENGINEER', TRUE, 30, 2, TRUE),

    (gen_random_uuid(), 'RUN_HEALTH_CHECK', 'Run Service Health Check',
     'Runs a read-only health check against the affected service to confirm current status before taking any corrective action.',
     'LOW', 'ENGINEER', FALSE, 10, 1, TRUE),

    (gen_random_uuid(), 'RETRY_FAILED_WORKFLOW', 'Retry Failed Workflow',
     'Re-triggers a failed asynchronous workflow (e.g. a stuck Kafka consumer or background job) from its last checkpoint.',
     'MEDIUM', 'ENGINEER', TRUE, 45, 2, TRUE);
