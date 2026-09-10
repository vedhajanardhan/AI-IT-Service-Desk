package com.servicedesk.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiAnalysisResponse(
        UUID id,
        UUID incidentId,
        String classification,
        String severityRecommendation,
        String priorityRecommendation,
        String rootCause,
        double confidenceScore,
        String recommendedActionCode,
        String explanation,
        List<String> relevantKnowledgeTags,
        String status,
        String failureReason,
        Instant createdAt
) {}
