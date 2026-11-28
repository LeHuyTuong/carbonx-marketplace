package com.carbonx.mrv.domain.shared;

/**
 * Raised when a workflow step is attempted from the wrong state (for example an admin decision
 * before the CVA review, or a second CVA decision). Mapped to HTTP 409 so callers can reload the
 * aggregate instead of retrying blindly.
 */
public class StateTransitionException extends RuntimeException {

    public StateTransitionException(String message) {
        super(message);
    }
}
