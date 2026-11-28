package com.carbonx.mrv.infrastructure.parser;

import com.carbonx.mrv.application.port.out.EmissionDatasetParserPort;
import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionRow;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RFC-4180-friendly CSV reader for the activity-data upload. Column names are normalised to the
 * canonical {@code period}/{@code total_energy}/{@code license_plate} names and malformed values are
 * kept raw so the DQ rules can score them instead of the parser dropping rows silently.
 */
@Component
public class CsvEmissionDatasetParser implements EmissionDatasetParserPort {

    private static final char BOM = '\uFEFF';

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("period", EmissionDataset.COL_PERIOD),
            Map.entry("billing_period", EmissionDataset.COL_PERIOD),
            Map.entry("reporting_period", EmissionDataset.COL_PERIOD),
            Map.entry("total_energy", EmissionDataset.COL_ENERGY),
            Map.entry("total_energy_kwh", EmissionDataset.COL_ENERGY),
            Map.entry("energy_kwh", EmissionDataset.COL_ENERGY),
            Map.entry("energy", EmissionDataset.COL_ENERGY),
            Map.entry("license_plate", EmissionDataset.COL_PLATE),
            Map.entry("plate_number", EmissionDataset.COL_PLATE),
            Map.entry("plate", EmissionDataset.COL_PLATE)
    );

    @Override
    public EmissionDataset parse(byte[] content) {
        if (content == null || content.length == 0) {
            return new EmissionDataset(List.of(), List.of());
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            return new EmissionDataset(List.of(), List.of());
        }

        List<String> rawHeaders = parseLine(lines.get(0));
        Map<String, Integer> columns = new HashMap<>();
        Set<String> present = new LinkedHashSet<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String canonical = HEADER_ALIASES.get(rawHeaders.get(index).trim().toLowerCase(Locale.ROOT));
            if (canonical != null) {
                columns.putIfAbsent(canonical, index);
                present.add(canonical);
            }
        }

        List<EmissionRow> rows = new ArrayList<>();
        for (int line = 1; line < lines.size(); line++) {
            List<String> cells = parseLine(lines.get(line));
            rows.add(new EmissionRow(
                    cell(cells, columns.get(EmissionDataset.COL_PERIOD)),
                    cell(cells, columns.get(EmissionDataset.COL_ENERGY)),
                    cell(cells, columns.get(EmissionDataset.COL_PLATE))));
        }
        return new EmissionDataset(List.copyOf(present), rows);
    }

    private static String cell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index);
        return value == null ? null : value.trim();
    }

    /** Minimal RFC-4180 cell splitter: double-quoted fields with {@code ""} escapes. */
    static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        current.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
