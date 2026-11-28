package com.carbonx.registry.domain.shared;

/**
 * Raised when an operation asks a {@code CreditHolding} for more credits than it can satisfy.
 * Credit quantities are physical serials, so they can never be overdrawn.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException(String message) {
        super(message);
    }
}
