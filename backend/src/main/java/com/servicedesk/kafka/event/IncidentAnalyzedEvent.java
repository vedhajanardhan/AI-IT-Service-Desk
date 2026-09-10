package com.servicedesk.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentAnalyzedEvent(
        UUID incidentId, String rootCause, double confidenceScore, Instant occurredAt) {}
