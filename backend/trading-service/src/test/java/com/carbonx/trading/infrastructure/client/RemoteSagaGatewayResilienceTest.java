package com.carbonx.trading.infrastructure.client;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.exception.TradingErrorCode;
import com.carbonx.trading.application.port.out.CreditHoldingProjectionPort.HoldingSnapshot;
import com.carbonx.trading.infrastructure.client.RegistryHoldingQueryClient.HoldingResponse;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                RemoteSagaGatewayResilienceTest.AopConfig.class,
                RemoteSagaGateway.class,
                RegistryHoldingProjectionAdapter.class
        },
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.openfeign.circuitbreaker.enabled=false",
                "resilience4j.circuitbreaker.circuit-breaker-aspect-order=1",
                "resilience4j.circuitbreaker.configs.default.ignore-exceptions[0]=com.carbonx.trading.application.exception.TradingApplicationException",
                "resilience4j.circuitbreaker.instances.ledgerSettlement.base-config=default",
                "resilience4j.circuitbreaker.instances.ledgerSettlement.sliding-window-size=4",
                "resilience4j.circuitbreaker.instances.ledgerSettlement.minimum-number-of-calls=2",
                "resilience4j.circuitbreaker.instances.ledgerSettlement.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.ledgerSettlement.wait-duration-in-open-state=30s",
                "resilience4j.circuitbreaker.instances.registryHoldings.base-config=default",
                "resilience4j.circuitbreaker.instances.registryHoldings.sliding-window-size=4",
                "resilience4j.circuitbreaker.instances.registryHoldings.minimum-number-of-calls=2",
                "resilience4j.circuitbreaker.instances.registryHoldings.failure-rate-threshold=50",
                "resilience4j.retry.retry-aspect-order=2",
                "resilience4j.retry.instances.ledgerSettlement.max-attempts=2",
                "resilience4j.retry.instances.ledgerSettlement.wait-duration=5ms",
                "resilience4j.retry.instances.ledgerSettlement.ignore-exceptions[0]=com.carbonx.trading.application.exception.TradingApplicationException",
                "resilience4j.retry.instances.ledgerSettlement.retry-exceptions[0]=feign.RetryableException",
                "resilience4j.retry.instances.ledgerSettlement.retry-exceptions[1]=feign.FeignException$FeignServerException",
                "resilience4j.retry.instances.ledgerSettlement.retry-exceptions[2]=java.io.IOException",
                "resilience4j.retry.instances.registryHoldings.max-attempts=2",
                "resilience4j.retry.instances.registryHoldings.wait-duration=5ms",
                "resilience4j.retry.instances.registryHoldings.ignore-exceptions[0]=com.carbonx.trading.application.exception.TradingApplicationException",
                "resilience4j.retry.instances.registryHoldings.retry-exceptions[0]=feign.RetryableException",
                "resilience4j.retry.instances.registryHoldings.retry-exceptions[1]=feign.FeignException$FeignServerException",
                "resilience4j.retry.instances.registryHoldings.retry-exceptions[2]=java.io.IOException"
        }
)
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class,
        BulkheadAutoConfiguration.class,
        RateLimiterAutoConfiguration.class
})
class RemoteSagaGatewayResilienceTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class AopConfig {
    }

    @MockBean
    private LedgerServiceClient ledgerClient;
    @MockBean
    private RegistryServiceClient registryClient;
    @MockBean
    private RegistryHoldingQueryClient holdingQueryClient;

    @Autowired
    private RemoteSagaGateway gateway;
    @Autowired
    private RegistryHoldingProjectionAdapter holdingAdapter;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        Mockito.reset(ledgerClient, registryClient, holdingQueryClient);
        circuitBreakerRegistry.circuitBreaker("ledgerSettlement").reset();
        circuitBreakerRegistry.circuitBreaker("registryHoldings").reset();
    }

    @Test
    void downstream409DoesNotRetryAndDoesNotTripCircuitBreaker() {
        doThrow(feignError(409, "{\"message\":\"Insufficient wallet balance\"}"))
                .when(ledgerClient).settleOrderPayment(eq(100L), any());

        for (int i = 0; i < 3; i++) {
            int attempt = i;
            assertThatThrownBy(() -> gateway.debitBuyerAndCreditSeller(
                    "k-" + attempt, 100L, 1L, 2L, Money.of(new BigDecimal("100")), Money.of(new BigDecimal("5"))))
                    .isInstanceOf(TradingApplicationException.class)
                    .satisfies(ex -> {
                        TradingApplicationException appEx = (TradingApplicationException) ex;
                        assertThat(appEx.code()).isEqualTo(TradingErrorCode.CONFLICT);
                        assertThat(appEx.getMessage()).isEqualTo("Insufficient wallet balance");
                    });
        }

        verify(ledgerClient, times(3)).settleOrderPayment(eq(100L), any());
        assertThat(circuitBreakerRegistry.circuitBreaker("ledgerSettlement").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void transient503RetriesAndSucceedsOnSecondAttempt() {
        doThrow(feignError(503, "{\"message\":\"temporarily overloaded\"}"))
                .doNothing()
                .when(ledgerClient).settleOrderPayment(eq(200L), any());

        gateway.debitBuyerAndCreditSeller(
                "k-retry", 200L, 1L, 2L, Money.of(new BigDecimal("100")), Money.of(new BigDecimal("5")));

        verify(ledgerClient, times(2)).settleOrderPayment(eq(200L), any());
        assertThat(circuitBreakerRegistry.circuitBreaker("ledgerSettlement").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void persistentRetryableTimeoutTripsCircuitBreakerAndFastFailsSubsequentCalls() {
        doThrow(retryableTimeout())
                .when(ledgerClient).settleOrderPayment(eq(300L), any());

        for (int i = 0; i < 2; i++) {
            int attempt = i;
            assertThatThrownBy(() -> gateway.debitBuyerAndCreditSeller(
                    "k-open-" + attempt, 300L, 1L, 2L, Money.of(new BigDecimal("100")), Money.of(new BigDecimal("5"))))
                    .isInstanceOf(TradingApplicationException.class)
                    .extracting(ex -> ((TradingApplicationException) ex).code())
                    .isEqualTo(TradingErrorCode.SERVICE_UNAVAILABLE);
        }

        verify(ledgerClient, times(4)).settleOrderPayment(eq(300L), any());
        assertThat(circuitBreakerRegistry.circuitBreaker("ledgerSettlement").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.debitBuyerAndCreditSeller(
                "k-open-3", 300L, 1L, 2L, Money.of(new BigDecimal("100")), Money.of(new BigDecimal("5"))))
                .isInstanceOf(TradingApplicationException.class)
                .extracting(ex -> ((TradingApplicationException) ex).code())
                .isEqualTo(TradingErrorCode.SERVICE_UNAVAILABLE);

        verify(ledgerClient, times(4)).settleOrderPayment(eq(300L), any());
    }

    @Test
    void holdingQuery403MapsToForbiddenWhileSuccessSnapshotsHolding() {
        when(holdingQueryClient.myHoldings()).thenThrow(feignError(403, "{\"message\":\"Company KYC required\"}"));

        assertThatThrownBy(() -> holdingAdapter.fetchOwnedHolding(10L))
                .isInstanceOf(TradingApplicationException.class)
                .extracting(ex -> ((TradingApplicationException) ex).code())
                .isEqualTo(TradingErrorCode.FORBIDDEN);

        verify(holdingQueryClient, times(1)).myHoldings();
        assertThat(circuitBreakerRegistry.circuitBreaker("registryHoldings").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        Mockito.reset(holdingQueryClient);
        when(holdingQueryClient.myHoldings()).thenReturn(List.of(
                new HoldingResponse(10L, 100L, "B-1", "Solar EV", 50L, 0L, 0L, "1..50")));
        HoldingSnapshot snap = holdingAdapter.fetchOwnedHolding(10L);
        assertThat(snap.availableQty()).isEqualTo(50L);
    }

    private static FeignException feignError(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST, "http://ledger-service/test", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("LedgerServiceClient#settleOrderPayment",
                feign.Response.builder()
                        .status(status)
                        .reason("err")
                        .request(request)
                        .headers(Collections.emptyMap())
                        .body(body, StandardCharsets.UTF_8)
                        .build());
    }

    private static RetryableException retryableTimeout() {
        Request request = Request.create(
                Request.HttpMethod.POST, "http://ledger-service/test", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        return new RetryableException(-1, "connect timed out", Request.HttpMethod.POST, 0L, request);
    }
}
