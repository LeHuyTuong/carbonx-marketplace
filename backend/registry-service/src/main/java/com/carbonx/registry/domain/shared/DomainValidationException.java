package com.carbonx.registry.domain.shared;

/** A business invariant was breached by a caller-supplied value. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
