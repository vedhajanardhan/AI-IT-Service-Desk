package com.servicedesk.remediation.executor;

public record RemediationExecutionOutcome(
        boolean success,
        String message,
        String failureReason
) {
    public static RemediationExecutionOutcome success(String message) {
        return new RemediationExecutionOutcome(true, message, null);
    }

    public static RemediationExecutionOutcome failure(String reason) {
        return new RemediationExecutionOutcome(false, null, reason);
    }
}
