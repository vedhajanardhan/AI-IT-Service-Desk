package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentCreatedEvent(
        UUID incidentId, String title, String category, String severity,
        UUID reporterId, Instant occurredAt) {}
