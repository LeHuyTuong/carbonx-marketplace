package com.carbonx.mrv.application.exception;

import org.springframework.http.HttpStatus;

/** MRV error catalogue and the HTTP status each code maps to. */
public enum MrvErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INVALID_STATE(HttpStatus.CONFLICT),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT),
    COMPANY_NOT_APPROVED(HttpStatus.CONFLICT),
    REPORT_NOT_ANALYZED(HttpStatus.CONFLICT),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    MrvErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
