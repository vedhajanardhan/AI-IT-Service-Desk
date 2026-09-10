package com.servicedesk.incident.dto;

import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.incident.IncidentPriority;
import com.servicedesk.incident.IncidentSeverity;
import com.servicedesk.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        IncidentCategory category,
        IncidentSeverity severity,
        IncidentPriority priority,
        IncidentStatus status,
        UUID reporterId,
        String reporterName,
        UUID assignedEngineerId,
        String assignedEngineerName,
        String resolutionDetails,
        int remediationAttempts,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant escalatedAt,
        String escalationReason
) {}
