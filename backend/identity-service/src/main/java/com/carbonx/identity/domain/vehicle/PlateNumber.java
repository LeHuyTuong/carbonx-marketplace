package com.carbonx.identity.domain.vehicle;

import com.carbonx.identity.domain.shared.DomainValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Licence plate value object. Normalises separators so "51A-123.45" and "51a 12345"
 * resolve to the same plate — plates are the join key MRV uses across services.
 */
public record PlateNumber(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[A-Z0-9]{4,15}");

    public PlateNumber {
        String normalized = value == null
                ? ""
                : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!ALLOWED.matcher(normalized).matches()) {
            throw new DomainValidationException("Invalid plate number: " + value);
        }
        value = normalized;
    }
}
