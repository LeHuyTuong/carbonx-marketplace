package com.carbonx.mrv.domain.report;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * One row of the uploaded activity-data CSV. Values stay raw (as uploaded) so the fraud rules can
 * score malformed input instead of the parser silently dropping it.
 */
public record EmissionRow(String period, String rawEnergy, String plateNumber) {

    public Optional<BigDecimal> energy() {
        if (rawEnergy == null || rawEnergy.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawEnergy.trim().replace(",", "");
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** Plate comparison key shared with identity-service PlateNumber normalisation (51A-123.45 -> 51A12345). */
    public String normalizedPlate() {
        if (plateNumber == null) {
            return "";
        }
        return plateNumber.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public String trimmedPeriod() {
        return period == null ? "" : period.trim();
    }

    public String signature() {
        return trimmedPeriod() + '|' + (rawEnergy == null ? "" : rawEnergy.trim()) + '|' + normalizedPlate();
    }
}
