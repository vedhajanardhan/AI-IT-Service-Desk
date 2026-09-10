package com.servicedesk.incident.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        String authorName,
        String body,
        boolean internalOnly,
        Instant createdAt
) {}
