package com.servicedesk.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveIncidentRequest(@NotBlank String resolutionDetails) {}
