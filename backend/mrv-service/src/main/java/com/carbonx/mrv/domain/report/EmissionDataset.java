package com.carbonx.mrv.domain.report;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parsed activity-data file: the canonical header names that were present plus every data row.
 * Totals are derived here so the same numbers feed the persisted report and the fraud rules.
 */
public record EmissionDataset(List<String> headers, List<EmissionRow> rows) {

    public static final String COL_PERIOD = "period";
    public static final String COL_ENERGY = "total_energy";
    public static final String COL_PLATE = "license_plate";
    public static final List<String> REQUIRED_HEADERS = List.of(COL_PERIOD, COL_ENERGY, COL_PLATE);

    private static final Pattern VALID_PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    public EmissionDataset {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean hasAllRequiredHeaders() {
        return headers.containsAll(REQUIRED_HEADERS);
    }

    public List<BigDecimal> energies() {
        return rows.stream().map(EmissionRow::energy).flatMap(java.util.Optional::stream).toList();
    }

    /** Sum of strictly positive kWh values; invalid rows are penalised by DQ3 instead of counted. */
    public BigDecimal totalEnergyKwh() {
        return energies().stream()
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Distinct normalised plates reported in the file. */
    public List<String> plates() {
        Set<String> distinct = new LinkedHashSet<>();
        for (EmissionRow row : rows) {
            String plate = row.normalizedPlate();
            if (!plate.isEmpty()) {
                distinct.add(plate);
            }
        }
        return List.copyOf(distinct);
    }

    public int vehicleCount() {
        return plates().size();
    }

    public static boolean isValidPeriod(String period) {
        return period != null && VALID_PERIOD.matcher(period.trim()).matches();
    }

    /** Most frequent valid {@code YYYY-MM}; falls back to the current UTC month for untrustworthy files. */
    public String resolvedPeriod() {
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (EmissionRow row : rows) {
            String period = row.trimmedPeriod();
            if (isValidPeriod(period)) {
                frequency.merge(period, 1, Integer::sum);
            }
        }
        return frequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> YearMonth.now(ZoneOffset.UTC).toString());
    }
}
