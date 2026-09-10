package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentAssignedEvent(
        UUID incidentId, UUID engineerId, UUID assignedByUserId, Instant occurredAt) {}
