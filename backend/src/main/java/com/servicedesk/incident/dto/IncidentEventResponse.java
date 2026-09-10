package com.servicedesk.incident.dto;

import com.servicedesk.incident.IncidentEventType;

import java.time.Instant;
import java.util.UUID;

public record IncidentEventResponse(
        UUID id,
        IncidentEventType eventType,
        String description,
        String actorName,
        String previousStatus,
        String newStatus,
        Instant createdAt
) {}
