package com.servicedesk.analytics;

import com.servicedesk.ai.AiAnalysisRepository;
import com.servicedesk.ai.AiAnalysisStatus;
import com.servicedesk.analytics.dto.AnalyticsSummaryResponse;
import com.servicedesk.analytics.dto.DailyCountPoint;
import com.servicedesk.incident.IncidentRepository;
import com.servicedesk.incident.IncidentStatus;
import com.servicedesk.remediation.RemediationExecutionRepository;
import com.servicedesk.remediation.RemediationExecutionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every number here is computed server-side from repository aggregate
 * queries, per the spec's "important metrics should be calculated by the
 * backend rather than only in the frontend." Results are cached for a
 * short TTL (see RedisConfig's "analyticsSummary" cache) since this is a
 * dashboard, not a real-time feed - a couple of minutes of staleness is
 * an acceptable trade for not re-running these aggregates on every page
 * load.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final IncidentRepository incidentRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final RemediationExecutionRepository remediationExecutionRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "analyticsSummary", key = "'summary:' + #from + ':' + #to")
    public AnalyticsSummaryResponse getSummary(Instant from, Instant to) {
        long total = incidentRepository.countByCreatedAtBetween(from, to);
        long resolved = incidentRepository.countByStatusAndCreatedAtBetween(IncidentStatus.RESOLVED, from, to);
        long escalated = incidentRepository.countByStatusAndCreatedAtBetween(IncidentStatus.ESCALATED, from, to);
        long open = total - resolved - escalated;

        Map<String, Long> bySeverity = toBucketMap(incidentRepository.countBySeverityBucket(from, to));
        Map<String, Long> byCategory = toBucketMap(incidentRepository.countByCategoryBucket(from, to));

        Double avgResolutionMinutes = incidentRepository.averageResolutionTimeMinutes(from, to);

        long aiSucceeded = aiAnalysisRepository.countByStatusAndCreatedAtBetween(AiAnalysisStatus.SUCCEEDED, from, to);
        long aiFailed = aiAnalysisRepository.countByStatusAndCreatedAtBetween(AiAnalysisStatus.FAILED, from, to);
        Double aiSuccessRate = percentageOf(aiSucceeded, aiSucceeded + aiFailed);

        long remediationSucceeded = remediationExecutionRepository
                .countByStatusAndCreatedAtBetween(RemediationExecutionStatus.SUCCEEDED, from, to);
        long remediationFailed = remediationExecutionRepository
                .countByStatusAndCreatedAtBetween(RemediationExecutionStatus.FAILED, from, to);
        long remediationTerminal = remediationSucceeded + remediationFailed;
        Double remediationSuccessRate = percentageOf(remediationSucceeded, remediationTerminal);
        Double remediationFailureRate = percentageOf(remediationFailed, remediationTerminal);

        long autoRemediatedResolved = incidentRepository
                .countByStatusAndRemediationAttemptsGreaterThanAndCreatedAtBetween(IncidentStatus.RESOLVED, 0, from, to);
        Double autoRemediationPercent = percentageOf(autoRemediatedResolved, resolved);

        return new AnalyticsSummaryResponse(from, to, total, open, resolved, escalated, bySeverity, byCategory,
                avgResolutionMinutes, aiSuccessRate, remediationSuccessRate, remediationFailureRate, autoRemediationPercent);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "analyticsSummary", key = "'daily:' + #from + ':' + #to")
    public List<DailyCountPoint> getIncidentsOverTime(Instant from, Instant to) {
        return incidentRepository.countCreatedPerDay(from, to).stream()
                .map(row -> new DailyCountPoint(
                        ((java.sql.Timestamp) row[0]).toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    private Map<String, Long> toBucketMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }

    private Double percentageOf(long part, long whole) {
        if (whole == 0) return null;
        return Math.round((part * 10000.0) / whole) / 100.0;
    }
}
