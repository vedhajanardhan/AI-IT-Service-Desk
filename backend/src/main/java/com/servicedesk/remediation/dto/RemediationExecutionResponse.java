package com.servicedesk.remediation.dto;

import java.time.Instant;
import java.util.UUID;

public record RemediationExecutionResponse(
        UUID id,
        UUID incidentId,
        String actionCode,
        String actionName,
        String status,
        String requestedByName,
        String approvedByName,
        int attemptNumber,
        Instant startedAt,
        Instant completedAt,
        String failureReason,
        Boolean healthCheckPassed,
        Instant createdAt
) {}
