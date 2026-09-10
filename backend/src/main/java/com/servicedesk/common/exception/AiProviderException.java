package com.servicedesk.common.exception;

import org.springframework.http.HttpStatus;

public class AiProviderException extends ApplicationException {
    public AiProviderException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_ERROR");
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_ERROR");
        initCause(cause);
    }
}
