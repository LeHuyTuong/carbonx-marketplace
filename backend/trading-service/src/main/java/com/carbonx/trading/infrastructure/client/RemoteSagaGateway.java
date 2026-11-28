package com.carbonx.trading.infrastructure.client;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.exception.TradingErrorCode;
import com.carbonx.trading.application.port.out.OrderCreditLockPort;
import com.carbonx.trading.application.port.out.OrderSagaRemotePorts;
import com.carbonx.trading.application.port.out.WalletRefundPort;
import com.carbonx.trading.infrastructure.client.LedgerServiceClient.RefundPaymentRequest;
import com.carbonx.trading.infrastructure.client.LedgerServiceClient.SettlePaymentRequest;
import com.carbonx.trading.infrastructure.client.RegistryServiceClient.LockRequest;
import com.carbonx.trading.infrastructure.client.RegistryServiceClient.ReleaseRequest;
import com.carbonx.trading.infrastructure.client.RegistryServiceClient.TransferRequest;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Every remote call of the Order Saga goes through this gateway. Each method is guarded by a
 * Resilience4j circuit breaker with an explicit fallback plus retry/bulkhead (or rate limiter).
 * Deterministic downstream 4xx business rejections pass straight through without tripping the
 * circuit breaker or retrying.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteSagaGateway implements OrderSagaRemotePorts, OrderCreditLockPort, WalletRefundPort {

    private final LedgerServiceClient ledgerClient;
    private final RegistryServiceClient registryClient;

    @Override
    @CircuitBreaker(name = "ledgerSettlement", fallbackMethod = "settlementFallback")
    @Retry(name = "ledgerSettlement")
    @Bulkhead(name = "ledgerSettlement")
    public void debitBuyerAndCreditSeller(String idempotencyKey, Long orderRef, Long buyerUserId,
                                          Long sellerUserId, Money gross, Money fee) {
        try {
            ledgerClient.settleOrderPayment(orderRef, new SettlePaymentRequest(idempotencyKey, buyerUserId,
                    sellerUserId, gross.amount(), fee.amount()));
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
    }

    public void settlementFallback(String idempotencyKey, Long orderRef, Long buyerUserId, Long sellerUserId,
                                   Money gross, Money fee, Throwable failure) {
        TradingApplicationException translated = FeignErrorTranslator.passthroughBusinessOrUnavailable(
                "ledger-service settlement of order " + orderRef, failure);
        if (translated.code() == TradingErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("ledger settlement unavailable order={} key={}: {}", orderRef, idempotencyKey,
                    failure == null ? "null" : failure.getMessage());
        }
        throw translated;
    }

    @Override
    @CircuitBreaker(name = "ledgerRefund", fallbackMethod = "refundFallback")
    @Retry(name = "ledgerRefund")
    public void refundOrderPayment(String idempotencyKey, Long orderRef, String reason) {
        try {
            ledgerClient.refundOrderPayment(orderRef, new RefundPaymentRequest(idempotencyKey, reason));
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
    }

    public void refundFallback(String idempotencyKey, Long orderRef, String reason, Throwable failure) {
        TradingApplicationException translated = FeignErrorTranslator.passthroughBusinessOrUnavailable(
                "ledger-service refund of order " + orderRef, failure);
        if (translated.code() == TradingErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("ledger refund unavailable order={} key={}: {}", orderRef, idempotencyKey,
                    failure == null ? "null" : failure.getMessage());
        }
        throw translated;
    }

    @Override
    @CircuitBreaker(name = "registryLock", fallbackMethod = "lockFallback")
    @Retry(name = "registryLock")
    @Bulkhead(name = "registryLock")
    public void lockCreditsForListing(String idempotencyKey, Long holdingId, Long sellerCompanyId, long quantity,
                                      String listingRef) {
        try {
            registryClient.lockCredits(holdingId,
                    new LockRequest(idempotencyKey, sellerCompanyId, quantity, listingRef));
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
    }

    public void lockFallback(String idempotencyKey, Long holdingId, Long sellerCompanyId, long quantity,
                             String listingRef, Throwable failure) {
        TradingApplicationException translated = FeignErrorTranslator.passthroughBusinessOrUnavailable(
                "registry-service lock of holding " + holdingId, failure);
        if (translated.code() == TradingErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("registry lock unavailable holding={} key={}: {}", holdingId, idempotencyKey,
                    failure == null ? "null" : failure.getMessage());
        }
        throw translated;
    }

    @Override
    @CircuitBreaker(name = "registryLock", fallbackMethod = "releaseFallback")
    @Retry(name = "registryLock")
    public void releaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason) {
        try {
            registryClient.releaseCredits(holdingId, new ReleaseRequest(idempotencyKey, quantity, reason));
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
    }

    public void releaseFallback(String idempotencyKey, Long holdingId, long quantity, String reason,
                                Throwable failure) {
        TradingApplicationException translated = FeignErrorTranslator.passthroughBusinessOrUnavailable(
                "registry-service release of holding " + holdingId, failure);
        if (translated.code() == TradingErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("registry release unavailable holding={} key={}: {}", holdingId, idempotencyKey,
                    failure == null ? "null" : failure.getMessage());
        }
        throw translated;
    }

    @Override
    @CircuitBreaker(name = "registryTransfer", fallbackMethod = "transferFallback")
    @Retry(name = "registryTransfer")
    @RateLimiter(name = "registryTransfer")
    public void transferCreditsToBuyer(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId,
                                       long quantity, String orderCode) {
        try {
            registryClient.transferCredits(sellerHoldingId,
                    new TransferRequest(idempotencyKey, buyerCompanyId, quantity, orderCode));
        } catch (FeignException ex) {
            throw FeignErrorTranslator.translateClientErrorOrRethrow(ex);
        }
    }

    public void transferFallback(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId, long quantity,
                                 String orderCode, Throwable failure) {
        TradingApplicationException translated = FeignErrorTranslator.passthroughBusinessOrUnavailable(
                "registry-service transfer from holding " + sellerHoldingId, failure);
        if (translated.code() == TradingErrorCode.SERVICE_UNAVAILABLE) {
            log.warn("registry transfer unavailable holding={} key={}: {}", sellerHoldingId, idempotencyKey,
                    failure == null ? "null" : failure.getMessage());
        }
        throw translated;
    }
}
