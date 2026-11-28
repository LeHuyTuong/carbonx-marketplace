package com.carbonx.registry.application.port.in;

import com.carbonx.mrv.api.EmissionReportApprovedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Inbound Use-Case Port: Immutable VM0038 Credit Batch Issuance & Partial Holding Management
 * (e.g., 100 issued -> 80 listed/sold -> 20 retained & retired with full serial tracking).
 */
public interface CreditRegistryUseCase {
    IssuancePreview previewIssuance(Long reportId);

    CreditBatchView issueBatch(EmissionReportApprovedEvent approvedEvent);

    List<CreditHoldingView> listMyHoldings(Long companyId);

    List<CreditMovementView> listHoldingMovements(Long companyId, Long holdingId);

    RetirementCertificateView retireCredits(Long companyId, RetireCreditsCommand command);

    byte[] exportRetirementCertificatePdf(Long companyId, Long certificateId);

    record IssuancePreview(String batchCode, int vintageYear, long creditsCount, BigDecimal totalTco2e, BigDecimal residualTco2e) {}

    record CreditBatchView(Long id, String batchCode, Long sourceReportId, String projectTitle, int vintageYear, long totalIssuedQty, BigDecimal totalTco2e, String serialRange, Instant issuedAt) {}

    record CreditHoldingView(Long holdingId, Long batchId, String batchCode, String projectTitle, long availableQty, long lockedQty, long retiredQty, String serialRange) {}

    record CreditMovementView(Long id, String movementType, long quantity, String serialRange, String referenceId, Instant createdAt) {}

    record RetireCreditsCommand(Long holdingId, long quantity, String beneficiaryName, String reason) {}

    record RetirementCertificateView(Long id, String certificateCode, Long batchId, String batchCode, long retiredQty, String serialRange, String beneficiaryName, String reason, Instant retiredAt) {}
}
