package com.carbonx.registry.application.exception;

import org.springframework.http.HttpStatus;

/** Registry error catalogue and the HTTP status each code maps to. */
public enum RegistryErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_CREDITS(HttpStatus.CONFLICT),
    /** The report carries no issuable credit; retrying the Kafka message cannot fix it. */
    UNISSUABLE_REPORT(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    RegistryErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
