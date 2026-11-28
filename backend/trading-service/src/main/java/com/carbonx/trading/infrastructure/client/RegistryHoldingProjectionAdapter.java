package com.carbonx.trading.infrastructure.client;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.out.CreditHoldingProjectionPort;
import com.carbonx.trading.infrastructure.client.RegistryHoldingQueryClient.HoldingResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Snapshots a company's own holding from registry-service before it is offered on the marketplace. */
@Component
@RequiredArgsConstructor
public class RegistryHoldingProjectionAdapter implements CreditHoldingProjectionPort {

    static final String DEPENDENCY = "registry-service holdings";

    private final RegistryHoldingQueryClient client;

    @Override
    @CircuitBreaker(name = "registryHoldings", fallbackMethod = "holdingFallback")
    @Retry(name = "registryHoldings")
    public HoldingSnapshot fetchOwnedHolding(Long holdingId) {
        if (holdingId == null) {
            throw TradingApplicationException.badRequest("holdingId is required");
        }
        List<HoldingResponse> holdings;
        try {
            holdings = client.myHoldings();
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
        if (holdings == null) {
            throw TradingApplicationException.unavailable(DEPENDENCY, null);
        }
        HoldingResponse holding = holdings.stream()
                .filter(candidate -> holdingId.equals(candidate.holdingId()))
                .findFirst()
                .orElseThrow(() -> TradingApplicationException.notFound(
                        "Holding " + holdingId + " does not belong to the current company"));
        return new HoldingSnapshot(holding.holdingId(), holding.batchId(), holding.batchCode(),
                holding.projectTitle(), holding.availableQty());
    }

    public HoldingSnapshot holdingFallback(Long holdingId, Throwable failure) {
        throw FeignErrorTranslator.passthroughBusinessOrUnavailable(DEPENDENCY, failure);
    }
}
