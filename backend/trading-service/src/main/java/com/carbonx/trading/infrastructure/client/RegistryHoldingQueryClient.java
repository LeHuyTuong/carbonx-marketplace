package com.carbonx.trading.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Company-scoped holdings of the caller, used to snapshot the batch when listing credits. The
 * forwarded bearer token makes registry-service return only the caller's own holdings.
 */
@FeignClient(name = "registry-service", url = "${carbonx.clients.registry.base-url:}",
        path = "/api/v1/holdings", contextId = "registryHoldingQueryClient")
public interface RegistryHoldingQueryClient {

    @GetMapping
    List<HoldingResponse> myHoldings();

    record HoldingResponse(Long holdingId, Long batchId, String batchCode, String projectTitle,
                           long availableQty, long lockedQty, long retiredQty, String serialRange) {
    }
}
