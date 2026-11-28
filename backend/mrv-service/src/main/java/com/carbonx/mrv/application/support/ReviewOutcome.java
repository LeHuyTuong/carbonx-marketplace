package com.carbonx.mrv.application.support;

import com.carbonx.mrv.application.exception.MrvApplicationException;

import java.util.Locale;

/** Parses the free-text review outcome sent by the CVA/admin UI into a boolean decision. */
public final class ReviewOutcome {

    private ReviewOutcome() {
    }

    public static boolean parse(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            throw MrvApplicationException.badRequest("Review outcome is required (APPROVE or REJECT)");
        }
        return switch (outcome.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED", "ACCEPT", "ACCEPTED", "TRUE", "PASS" -> true;
            case "REJECT", "REJECTED", "DENY", "DENIED", "FALSE", "FAIL" -> false;
            default -> throw MrvApplicationException.badRequest(
                    "Unsupported review outcome: " + outcome + " (expected APPROVE or REJECT)");
        };
    }
}
