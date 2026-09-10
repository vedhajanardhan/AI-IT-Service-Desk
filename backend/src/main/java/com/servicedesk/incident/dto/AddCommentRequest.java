package com.servicedesk.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCommentRequest(
        @NotBlank String body,
        boolean internalOnly
) {}
