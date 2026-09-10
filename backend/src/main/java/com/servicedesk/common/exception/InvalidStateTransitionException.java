package com.servicedesk.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends ApplicationException {
    public InvalidStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION");
    }
}
