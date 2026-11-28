package com.carbonx.registry.domain.credit;

import java.math.BigDecimal;

/**
 * Result of the VM0038 arithmetic: adjusted tonnage, the whole credits that can be minted, and the
 * fractional tonnage carried into the next vintage period.
 */
public record IssuanceComputation(BigDecimal grossTco2e, BigDecimal netTco2e, BigDecimal residualTco2e,
                                  long credits) {

    public boolean isIssuable() {
        return credits > 0;
    }
}
