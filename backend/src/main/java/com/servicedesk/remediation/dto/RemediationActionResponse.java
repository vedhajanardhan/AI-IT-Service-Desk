package com.servicedesk.remediation.dto;

import java.util.UUID;

public record RemediationActionResponse(
        UUID id,
        String code,
        String name,
        String description,
        String riskLevel,
        String requiredRole,
        boolean approvalRequired,
        int timeoutSeconds,
        int retryLimit,
        boolean enabled
) {}
