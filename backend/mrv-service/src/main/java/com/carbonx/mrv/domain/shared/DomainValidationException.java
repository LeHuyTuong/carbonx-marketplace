package com.carbonx.mrv.domain.shared;

/** Raised when an aggregate invariant is violated; mapped to HTTP 400 by the API layer. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
