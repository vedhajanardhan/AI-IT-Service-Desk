package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentEscalatedEvent(
        UUID incidentId, String reason, Instant occurredAt) {}
