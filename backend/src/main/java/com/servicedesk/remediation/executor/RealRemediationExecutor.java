package com.servicedesk.remediation.executor;

import com.servicedesk.common.exception.RemediationExecutionException;
import com.servicedesk.remediation.RemediationAction;
import org.springframework.stereotype.Component;

/**
 * Placeholder for a real infrastructure integration (calling your
 * orchestration platform's API - e.g. Kubernetes, a service mesh control
 * plane, cloud provider APIs, or an internal ops automation service - to
 * actually restart a service, clear a cache, etc.).
 *
 * Deliberately not implemented here: wiring this up requires real
 * credentials and a real target environment, which don't exist in a
 * portfolio project. Selected via REMEDIATION_EXECUTOR=real, at which
 * point each `case` below should call the relevant real API instead of
 * throwing. The dispatch-by-code shape mirrors MockRemediationExecutor
 * intentionally, so swapping executors requires no changes anywhere else
 * in the codebase - RemediationService only ever depends on the
 * RemediationExecutor interface.
 */
@Component
public class RealRemediationExecutor implements RemediationExecutor {

    @Override
    public RemediationExecutionOutcome execute(RemediationAction action, ExecutionContext context) {
        throw new RemediationExecutionException(
                "No real infrastructure integration is configured for action '" + action.getCode() +
                        "'. Implement RealRemediationExecutor against your actual infrastructure APIs, " +
                        "or set REMEDIATION_EXECUTOR=mock for local/demo use.");
    }

    @Override
    public String executorName() {
        return "real";
    }
}
