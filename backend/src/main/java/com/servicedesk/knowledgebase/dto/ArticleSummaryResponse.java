package com.servicedesk.knowledgebase.dto;

import com.servicedesk.incident.IncidentCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleSummaryResponse(
        UUID id,
        String title,
        String summary,
        IncidentCategory category,
        List<String> tags,
        String status,
        Instant updatedAt
) {}
