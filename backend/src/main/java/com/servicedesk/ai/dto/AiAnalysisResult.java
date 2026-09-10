package com.servicedesk.ai.dto;

import java.util.List;

/**
 * Structured output every AI provider must return, regardless of whether
 * it's the mock provider or a real LLM. confidenceScore is expected in
 * [0.0, 1.0]; recommendedActionCode must match a known RemediationAction
 * code (or be null if no safe automated action applies).
 */
public record AiAnalysisResult(
        String classification,
        String severityRecommendation,
        String priorityRecommendation,
        String rootCause,
        double confidenceScore,
        String recommendedActionCode,
        String explanation,
        List<String> relevantKnowledgeTags
) {}
