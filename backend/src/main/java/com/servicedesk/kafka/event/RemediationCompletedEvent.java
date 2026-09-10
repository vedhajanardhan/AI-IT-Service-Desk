package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationCompletedEvent(
        UUID incidentId, UUID remediationExecutionId, boolean healthCheckPassed, Instant occurredAt) {}
