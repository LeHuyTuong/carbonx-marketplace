package com.carbonx.trading.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal registry channel used by the saga to lock, release and transfer credit serials. */
@FeignClient(name = "registry-service", url = "${carbonx.clients.registry.base-url:}",
        path = "/api/v1/internal/holdings")
public interface RegistryServiceClient {

    @PostMapping("/{holdingId}/lock")
    LockResponse lockCredits(@PathVariable("holdingId") Long holdingId, @RequestBody LockRequest request);

    @PostMapping("/{holdingId}/release")
    void releaseCredits(@PathVariable("holdingId") Long holdingId, @RequestBody ReleaseRequest request);

    @PostMapping("/{holdingId}/transfer")
    TransferResponse transferCredits(@PathVariable("holdingId") Long holdingId,
                                     @RequestBody TransferRequest request);

    record LockRequest(String idempotencyKey, Long sellerCompanyId, long quantity, String listingRef) {
    }

    record LockResponse(Long holdingId, Long batchId, String batchCode, String projectTitle, long lockedQty) {
    }

    record ReleaseRequest(String idempotencyKey, long quantity, String reason) {
    }

    record TransferRequest(String idempotencyKey, Long buyerCompanyId, long quantity, String orderRef) {
    }

    record TransferResponse(Long buyerHoldingId, Long batchId, long serialStart, long serialEnd) {
    }
}
