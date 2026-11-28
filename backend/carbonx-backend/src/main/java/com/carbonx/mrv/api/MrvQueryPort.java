package com.carbonx.mrv.api;

import java.math.BigDecimal;
import java.util.List;

/** Public Query Port exposed by MRV Bounded Context so Ledger can compute EV Owner profit sharing. */
public interface MrvQueryPort {
    ReportContributionSnapshot getApprovedReportContributions(Long reportId);

    record ReportContributionSnapshot(
            Long reportId,
            Long companyId,
            Long projectId,
            String projectTitle,
            String period,
            BigDecimal totalEnergyKwh,
            List<OwnerEnergyContribution> ownerContributions
    ) {}

    record OwnerEnergyContribution(Long evOwnerId, String ownerEmail, int vehicleCount, BigDecimal energyKwh) {}
}
