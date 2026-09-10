package com.servicedesk.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedActionException extends ApplicationException {
    public UnauthorizedActionException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACTION");
    }
}
