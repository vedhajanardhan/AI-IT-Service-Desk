package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationStartedEvent(
        UUID incidentId, UUID remediationExecutionId, int attemptNumber, Instant occurredAt) {}
