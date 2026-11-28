package com.carbonx.mrv.domain.fraud;

import com.carbonx.mrv.domain.report.EmissionDataset;

/** A single pure data-quality rule over an uploaded dataset. */
public interface FraudRule {
    FraudFinding apply(EmissionDataset dataset);
}
