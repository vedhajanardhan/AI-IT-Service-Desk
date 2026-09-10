package com.servicedesk.remediation.health;

/**
 * Validates that a remediation action actually fixed things, rather than
 * just trusting the executor's "success" return value. Kept as its own
 * interface (separate from RemediationExecutor) because in a real system
 * this often checks a completely different signal - e.g. "did error rate
 * drop in the last 60 seconds" rather than "did the restart command
 * return 0".
 */
public interface HealthChecker {
    boolean check(String incidentId, String actionCode);
}
