package com.carbonx.trading.infrastructure.scheduler;

import com.carbonx.trading.application.service.OrderSagaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically sweeps timed-out Order Sagas so a pod crash never leaves reservations stuck. */
@Component
@Slf4j
public class OrderSagaRecoveryScheduler {

    private final OrderSagaService orderSagaService;
    private final int batchSize;

    public OrderSagaRecoveryScheduler(OrderSagaService orderSagaService,
                                      @Value("${carbonx.saga.recovery-batch-size:50}") int batchSize) {
        this.orderSagaService = orderSagaService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${carbonx.saga.recovery-interval-ms:30000}")
    public void sweepTimedOutSagas() {
        try {
            int recovered = orderSagaService.recoverTimedOutSagas(batchSize);
            if (recovered > 0) {
                log.info("Recovered {} timed-out Order Saga instances", recovered);
            }
        } catch (RuntimeException ex) {
            log.error("Timed-out saga recovery tick failed: {}", ex.getMessage());
        }
    }
}
