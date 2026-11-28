package com.carbonx.trading.application.exception;

import org.springframework.http.HttpStatus;

/** Trading error catalogue and the HTTP status each code maps to. */
public enum TradingErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CONFLICT(HttpStatus.CONFLICT),
    LISTING_UNAVAILABLE(HttpStatus.CONFLICT),
    SAGA_IN_PROGRESS(HttpStatus.CONFLICT),
    /** The saga rolled back; a retried request cannot succeed without a new idempotency key. */
    SAGA_COMPENSATED(HttpStatus.CONFLICT),
    /** A remote saga step (ledger/registry) is unavailable; the caller may retry the same key. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    TradingErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
