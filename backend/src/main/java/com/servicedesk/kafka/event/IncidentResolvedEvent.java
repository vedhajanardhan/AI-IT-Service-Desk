package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentResolvedEvent(
        UUID incidentId, boolean autoRemediated, Instant occurredAt) {}
