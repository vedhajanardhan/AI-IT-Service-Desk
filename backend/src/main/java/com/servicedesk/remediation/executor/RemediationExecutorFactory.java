package com.servicedesk.remediation.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RemediationExecutorFactory {

    private final MockRemediationExecutor mockExecutor;
    private final RealRemediationExecutor realExecutor;
    private final String activeExecutorName;

    public RemediationExecutorFactory(
            MockRemediationExecutor mockExecutor,
            RealRemediationExecutor realExecutor,
            @Value("${app.remediation.executor}") String activeExecutorName) {
        this.mockExecutor = mockExecutor;
        this.realExecutor = realExecutor;
        this.activeExecutorName = activeExecutorName;
    }

    public RemediationExecutor getActiveExecutor() {
        return "real".equalsIgnoreCase(activeExecutorName) ? realExecutor : mockExecutor;
    }
}
