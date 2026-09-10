package com.servicedesk.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record EscalateIncidentRequest(@NotBlank String reason) {}
