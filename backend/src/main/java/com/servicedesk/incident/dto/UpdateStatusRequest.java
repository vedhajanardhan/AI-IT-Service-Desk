package com.servicedesk.incident.dto;

import com.servicedesk.incident.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull IncidentStatus newStatus,
        String reason
) {}
