package com.servicedesk.remediation.executor;

import com.servicedesk.remediation.RemediationAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates running each catalog action without touching any real
 * infrastructure - this is what lets the whole platform be demoed and
 * tested end-to-end with zero external dependencies. Each action code
 * gets a small, believable simulated delay and a mostly-success outcome,
 * with an occasional simulated failure so the retry/escalate path is
 * exercised too rather than looking untested.
 */
@Component
public class MockRemediationExecutor implements RemediationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MockRemediationExecutor.class);

    /** Roughly 1-in-6 simulated executions "fail" so retry/escalation paths are reachable in a demo. */
    private static final double SIMULATED_FAILURE_RATE = 0.15;

    @Override
    public RemediationExecutionOutcome execute(RemediationAction action, ExecutionContext context) {
        log.info("[MOCK EXECUTOR] Running action '{}' for incident {} (correlationId={})",
                action.getCode(), context.incidentId(), context.correlationId());

        simulateWork();

        if (ThreadLocalRandom.current().nextDouble() < SIMULATED_FAILURE_RATE) {
            String reason = "Simulated failure: " + action.getName() + " did not complete within expected parameters";
            log.warn("[MOCK EXECUTOR] {}", reason);
            return RemediationExecutionOutcome.failure(reason);
        }

        String message = switch (action.getCode()) {
            case "RESTART_APPLICATION_SERVICE" -> "Application service restarted successfully";
            case "CLEAR_APPLICATION_CACHE" -> "Application cache cleared successfully";
            case "INVALIDATE_REDIS_CACHE" -> "Redis cache keys invalidated successfully";
            case "RESTART_CONNECTION_POOL" -> "Database connection pool restarted successfully";
            case "RUN_HEALTH_CHECK" -> "Health check executed successfully";
            case "RETRY_FAILED_WORKFLOW" -> "Failed workflow retried successfully";
            default -> action.getName() + " completed successfully";
        };
        return RemediationExecutionOutcome.success(message);
    }

    private void simulateWork() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(200, 600));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String executorName() {
        return "mock";
    }
}
