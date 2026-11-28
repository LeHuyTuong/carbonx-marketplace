package com.carbonx.ledger.application.exception;

import org.springframework.http.HttpStatus;

public enum LedgerErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    CONFLICT(HttpStatus.CONFLICT);

    private final HttpStatus status;

    LedgerErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus httpStatus() {
        return status;
    }
}
