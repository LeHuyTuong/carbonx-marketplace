package com.carbonx.trading.domain.shared;

/** A trading aggregate invariant was violated by the caller's input. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
