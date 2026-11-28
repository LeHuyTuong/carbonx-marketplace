package com.carbonx.mrv.application.port.out;

import com.carbonx.mrv.domain.report.EmissionDataset;

/** Parses an uploaded activity-data file into the fraud-rule input model. */
public interface EmissionDatasetParserPort {
    EmissionDataset parse(byte[] content);
}
