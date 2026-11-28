package com.carbonx.trading.application.port.out;

/**
 * Read-only view of a registry holding, used when a company lists credits: the marketplace keeps a
 * snapshot of the batch so browsing never calls registry-service.
 */
public interface CreditHoldingProjectionPort {

    HoldingSnapshot fetchOwnedHolding(Long holdingId);

    record HoldingSnapshot(Long holdingId, Long batchId, String batchCode, String projectTitle,
                           long availableQty) {
    }
}
