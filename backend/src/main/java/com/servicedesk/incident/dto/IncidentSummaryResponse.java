package com.servicedesk.incident.dto;

import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.incident.IncidentSeverity;
import com.servicedesk.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

/** Lighter-weight shape for list/table views. */
public record IncidentSummaryResponse(
        UUID id,
        String title,
        IncidentCategory category,
        IncidentSeverity severity,
        IncidentStatus status,
        String assignedEngineerName,
        Instant createdAt
) {}
