package com.servicedesk.ai.provider;

import com.servicedesk.ai.dto.AiAnalysisRequest;
import com.servicedesk.ai.dto.AiAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Rule-based "AI" that lets the whole platform run and demo locally with
 * zero external dependencies and no API key. It looks for known incident
 * signatures in the title/description and returns a plausible, structured
 * analysis - deliberately simple, deterministic, and easy to reason about
 * in a demo or interview, unlike a real LLM's output.
 */
@Component
public class MockAiProvider implements AiProvider {

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        String text = (request.title() + " " + request.description()).toLowerCase(Locale.ROOT);

        if (containsAny(text, "redis", "cache miss", "cache connection")) {
            return new AiAnalysisResult(
                    "INFRASTRUCTURE", "HIGH", "P2_HIGH",
                    "Redis connection pool appears exhausted or the cache node is unreachable, " +
                            "causing downstream requests to fall through to the database under load.",
                    0.82, "INVALIDATE_REDIS_CACHE",
                    "Detected Redis-related keywords. Recommending a cache invalidation and connection " +
                            "pool restart, which resolves the vast majority of stale-cache and stuck-connection issues.",
                    List.of("redis-connectivity", "caching"));
        }

        if (containsAny(text, "database", "db connection", "connection pool", "sql timeout", "deadlock")) {
            return new AiAnalysisResult(
                    "DATABASE", "HIGH", "P2_HIGH",
                    "Database connection pool is likely saturated, or a long-running query is holding " +
                            "connections open, starving other requests.",
                    0.78, "RESTART_CONNECTION_POOL",
                    "Detected database-related keywords. Recommending a connection pool restart before " +
                            "escalating to a DBA for query-level investigation.",
                    List.of("database-connection-problems"));
        }

        if (containsAny(text, "401", "403", "auth", "login fail", "token expired", "unauthorized")) {
            return new AiAnalysisResult(
                    "AUTHENTICATION", "MEDIUM", "P3_MEDIUM",
                    "Authentication failures are consistent with an expired signing key, clock skew, or " +
                            "an identity-provider outage rather than a client-side bug.",
                    0.70, "RESTART_APPLICATION_SERVICE",
                    "Detected authentication-failure keywords. A service restart clears cached/expired " +
                            "credentials in most cases; escalate if failures persist after restart.",
                    List.of("authentication-failures"));
        }

        if (containsAny(text, "500", "503", "service unavailable", "internal server error")) {
            return new AiAnalysisResult(
                    "APPLICATION_ERROR", "HIGH", "P2_HIGH",
                    "Recurring 5xx responses typically indicate an unhandled exception path or a downstream " +
                            "dependency timing out.",
                    0.65, "RESTART_APPLICATION_SERVICE",
                    "Detected HTTP 5xx keywords. Recommending a service restart as the safe first step; " +
                            "if it recurs shortly after, this needs human investigation of application logs.",
                    List.of("http-500-503-errors"));
        }

        if (containsAny(text, "cpu", "high load", "cpu spike")) {
            return new AiAnalysisResult(
                    "PERFORMANCE", "MEDIUM", "P3_MEDIUM",
                    "Sustained high CPU usually points to an inefficient query, a runaway background job, " +
                            "or a traffic spike outpacing current instance sizing.",
                    0.60, "RUN_HEALTH_CHECK",
                    "Detected CPU-related keywords. Recommending a health check first to confirm current " +
                            "load, since restarting under a genuine traffic spike would not help.",
                    List.of("high-cpu-usage"));
        }

        if (containsAny(text, "disk space", "disk full", "no space left")) {
            return new AiAnalysisResult(
                    "INFRASTRUCTURE", "CRITICAL", "P1_URGENT",
                    "Disk space exhaustion, most commonly from unrotated logs or temp files, will cause " +
                            "writes to fail across the service until space is reclaimed.",
                    0.75, null,
                    "Detected disk-space keywords. No safe automated remediation exists for disk cleanup " +
                            "in this system - flagging for immediate human attention rather than guessing.",
                    List.of("disk-space-issues"));
        }

        if (containsAny(text, "kafka", "consumer lag", "partition")) {
            return new AiAnalysisResult(
                    "INFRASTRUCTURE", "MEDIUM", "P3_MEDIUM",
                    "Growing consumer lag usually means a consumer group is processing slower than the " +
                            "producer rate, or a consumer instance is stuck/rebalancing repeatedly.",
                    0.68, "RETRY_FAILED_WORKFLOW",
                    "Detected Kafka-related keywords. Recommending a retry of the stuck workflow; if lag " +
                            "keeps growing, this needs a look at consumer scaling.",
                    List.of("kafka-consumer-lag"));
        }

        // Generic fallback - still structured and useful, just lower confidence.
        return new AiAnalysisResult(
                "OTHER", request.severity(), "P3_MEDIUM",
                "No known incident signature matched; this requires a human engineer to investigate " +
                        "the specifics before a remediation path can be recommended with confidence.",
                0.35, null,
                "Could not confidently match this incident against known patterns. Recommending manual " +
                        "triage rather than an automated action.",
                List.of());
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
