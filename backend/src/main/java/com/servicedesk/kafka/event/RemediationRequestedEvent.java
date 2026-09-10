package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationRequestedEvent(
        UUID incidentId, UUID remediationExecutionId, String actionId, Instant occurredAt) {}
