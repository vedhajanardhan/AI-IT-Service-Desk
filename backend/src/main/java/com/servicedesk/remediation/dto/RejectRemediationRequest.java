package com.servicedesk.remediation.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRemediationRequest(@NotBlank String reason) {}
