package com.servicedesk.remediation.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates a post-remediation health check. In a real deployment this
 * would query actual service metrics/health endpoints; here it mostly
 * passes (remediation execution already simulated its own failure rate),
 * so a genuine health-check-specific failure is rare but still possible -
 * exercising the VALIDATING -> REMEDIATION_PENDING retry path.
 */
@Component
public class MockHealthChecker implements HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(MockHealthChecker.class);
    private static final double SIMULATED_HEALTH_CHECK_FAILURE_RATE = 0.05;

    @Override
    public boolean check(String incidentId, String actionCode) {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean passed = ThreadLocalRandom.current().nextDouble() >= SIMULATED_HEALTH_CHECK_FAILURE_RATE;
        log.info("[MOCK HEALTH CHECK] incident={} action={} passed={}", incidentId, actionCode, passed);
        return passed;
    }
}
