package com.servicedesk.ai.dto;

import java.util.UUID;

/** What we send the AI provider - just enough to classify and diagnose. */
public record AiAnalysisRequest(
        UUID incidentId,
        String title,
        String description,
        String category,
        String severity
) {}
