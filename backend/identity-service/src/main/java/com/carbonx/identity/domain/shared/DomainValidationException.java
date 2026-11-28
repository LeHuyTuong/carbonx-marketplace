package com.carbonx.identity.domain.shared;

/** Raised when an aggregate or value object would be created in an invalid state. */
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) {
        super(message);
    }
}
