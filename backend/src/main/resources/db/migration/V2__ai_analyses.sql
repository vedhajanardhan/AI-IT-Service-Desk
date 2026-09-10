CREATE TABLE ai_analyses (
    id                          UUID PRIMARY KEY,
    incident_id                 UUID         NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    status                      VARCHAR(20)  NOT NULL,
    provider                    VARCHAR(30),
    classification              VARCHAR(30),
    severity_recommendation     VARCHAR(20),
    priority_recommendation     VARCHAR(20),
    root_cause                  TEXT,
    confidence_score            DOUBLE PRECISION,
    recommended_action_code     VARCHAR(50),
    explanation                 TEXT,
    relevant_knowledge_tags     VARCHAR(500),
    failure_reason              TEXT,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_ai_analyses_incident ON ai_analyses (incident_id);
CREATE INDEX idx_ai_analyses_status ON ai_analyses (status);
