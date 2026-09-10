package com.servicedesk.incident.dto;

import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.incident.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull IncidentCategory category,
        @NotNull IncidentSeverity severity
) {}
