package com.servicedesk.remediation.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestRemediationRequest(@NotBlank String actionCode) {}
