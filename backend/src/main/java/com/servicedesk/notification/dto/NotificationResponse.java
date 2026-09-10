package com.servicedesk.notification.dto;

import com.servicedesk.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        UUID relatedIncidentId,
        boolean read,
        Instant createdAt
) {}
