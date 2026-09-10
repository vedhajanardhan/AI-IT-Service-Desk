package com.servicedesk.analytics.dto;

import java.time.Instant;
import java.util.Map;

public record AnalyticsSummaryResponse(
        Instant from,
        Instant to,
        long totalIncidents,
        long openIncidents,
        long resolvedIncidents,
        long escalatedIncidents,
        Map<String, Long> incidentsBySeverity,
        Map<String, Long> incidentsByCategory,
        Double averageResolutionTimeMinutes,
        Double aiAnalysisSuccessRatePercent,
        Double remediationSuccessRatePercent,
        Double remediationFailureRatePercent,
        Double autoRemediationPercent
) {}
