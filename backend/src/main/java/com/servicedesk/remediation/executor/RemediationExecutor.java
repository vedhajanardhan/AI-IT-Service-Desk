package com.servicedesk.remediation.executor;

import com.servicedesk.remediation.RemediationAction;

/**
 * The ONLY interface through which remediation actions run. This is a
 * closed set of operations dispatched by {@code action.getCode()} - there
 * is deliberately no method here that accepts a free-form command string.
 * Nothing upstream of this (AI provider, REST API, Kafka consumer) can
 * cause arbitrary code/shell execution; the worst a compromised or
 * hallucinating AI recommendation can do is pick the wrong item from this
 * fixed catalog, and even that requires an enabled action plus (for
 * approval-required actions) a human engineer's explicit approval first.
 *
 * A real infrastructure integration (calling actual orchestration APIs,
 * SSH, Kubernetes, etc.) implements this same interface - see
 * RealRemediationExecutor for where that plugs in.
 */
public interface RemediationExecutor {

    RemediationExecutionOutcome execute(RemediationAction action, ExecutionContext context);

    String executorName();

    record ExecutionContext(String incidentId, String correlationId) {}
}
