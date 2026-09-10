package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationFailedEvent(
        UUID incidentId, UUID remediationExecutionId, String failureReason,
        int attemptNumber, boolean willRetry, Instant occurredAt) {}
