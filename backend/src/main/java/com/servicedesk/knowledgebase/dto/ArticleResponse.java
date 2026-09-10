package com.servicedesk.knowledgebase.dto;

import com.servicedesk.incident.IncidentCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String title,
        String content,
        String summary,
        IncidentCategory category,
        List<String> tags,
        String status,
        String authorName,
        Instant createdAt,
        Instant updatedAt
) {}
