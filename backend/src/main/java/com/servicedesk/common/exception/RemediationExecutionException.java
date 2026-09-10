package com.servicedesk.common.exception;

import org.springframework.http.HttpStatus;

public class RemediationExecutionException extends ApplicationException {
    public RemediationExecutionException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "REMEDIATION_EXECUTION_ERROR");
    }
}
