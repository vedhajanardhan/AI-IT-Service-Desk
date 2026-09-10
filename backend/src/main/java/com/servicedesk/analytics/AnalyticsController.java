package com.servicedesk.analytics;

import com.servicedesk.analytics.dto.AnalyticsSummaryResponse;
import com.servicedesk.analytics.dto.DailyCountPoint;
import com.servicedesk.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Server-computed incident, AI, and remediation metrics")
public class AnalyticsController {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] range = resolveRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getSummary(range[0], range[1])));
    }

    @GetMapping("/incidents-over-time")
    public ResponseEntity<ApiResponse<List<DailyCountPoint>>> incidentsOverTime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant[] range = resolveRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getIncidentsOverTime(range[0], range[1])));
    }

    private Instant[] resolveRange(Instant from, Instant to) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        return new Instant[] { resolvedFrom, resolvedTo };
    }
}
