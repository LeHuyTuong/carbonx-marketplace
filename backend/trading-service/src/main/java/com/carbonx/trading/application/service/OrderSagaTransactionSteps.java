package com.carbonx.trading.application.service;

import com.carbonx.common.events.IntegrationEvents.OrderCompensatedEvent;
import com.carbonx.common.events.IntegrationEvents.OrderSettledEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.out.ListingReservationRepositoryPort;
import com.carbonx.trading.application.port.out.MarketListingRepositoryPort;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort;
import com.carbonx.trading.application.port.out.PlatformSettingRepositoryPort;
import com.carbonx.trading.application.port.out.TradeOrderRepositoryPort;
import com.carbonx.trading.domain.order.ListingReservation;
import com.carbonx.trading.domain.order.ListingStatus;
import com.carbonx.trading.domain.order.MarketListing;
import com.carbonx.trading.domain.order.OrderPricing;
import com.carbonx.trading.domain.order.TradeOrder;
import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Every local write of the Order Saga, each in its own short database transaction. Keeping them
 * here (instead of inside the orchestrator) means a saga never holds a database row lock while it
 * waits for ledger-service or registry-service over HTTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaTransactionSteps {

    private static final Duration SAGA_TIMEOUT = Duration.ofMinutes(5);
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MarketListingRepositoryPort listingRepository;
    private final TradeOrderRepositoryPort orderRepository;
    private final ListingReservationRepositoryPort reservationRepository;
    private final OrderSagaOrchestratorPort orchestrator;
    private final OutboxPort outbox;
    private final PlatformSettingRepositoryPort platformSettingRepository;

    /**
     * Step 1 (RESERVE_LISTING) local half: lock the listing row, hold the quantity, create the
     * PENDING order and start the saga at EXECUTING. All of it commits atomically, so a crash can
     * never leave reserved quantity without an order (or the reverse).
     */
    @Transactional
    public SagaReservationContext reserveListingAndStartSaga(String idempotencyKey, Long buyerCompanyId,
                                                             Long listingId, long quantity) {
        MarketListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> TradingApplicationException.notFound("Listing not found: " + listingId));
        if (listing.isOwnedBy(buyerCompanyId)) {
            throw TradingApplicationException.forbidden("A company cannot buy its own listing " + listingId);
        }
        if (listing.status() != ListingStatus.LISTED) {
            throw TradingApplicationException.listingUnavailable(
                    "Listing " + listingId + " is " + listing.status());
        }

        Instant now = Instant.now();
        MarketListing reserved = listing.reserve(quantity, now);
        BigDecimal activeFeeRate = platformSettingRepository.getTradingFeeRate();
        OrderPricing pricing = OrderPricing.of(listing.pricePerCredit(), quantity, activeFeeRate);
        TradeOrder order = orderRepository.save(
                TradeOrder.place(nextOrderCode(), idempotencyKey, listing, pricing, buyerCompanyId));
        ListingReservation reservation = reservationRepository.save(ListingReservation.reserve(
                listingId, buyerCompanyId, idempotencyKey, quantity, now.plus(SAGA_TIMEOUT)));
        listingRepository.save(reserved);

        String sagaId = orchestrator.beginSaga(idempotencyKey, order.id(), listingId, now.plus(SAGA_TIMEOUT));
        orchestrator.transitionState(sagaId, OrderSagaState.PENDING, OrderSagaState.EXECUTING,
                OrderSagaStep.RESERVE_LISTING, "listing reserved for order " + order.orderCode());

        log.info("Saga {} reserved {} credits of listing {} for buyer company {} (order {})",
                sagaId, quantity, listingId, buyerCompanyId, order.orderCode());
        return new SagaReservationContext(sagaId, order.id(), order.orderCode(), reservation.id(), listingId,
                listing.holdingId(), listing.sellerCompanyId(), listing.effectiveSellerUserId(), quantity,
                pricing.grossAmount(), pricing.platformFee());
    }

    /** Step 4 (FINALIZE_ORDER): commit the reservation, finish the order, complete the saga + outbox. */
    @Transactional
    public void finalizeOrder(String sagaId, Long orderId, Long reservationId) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> TradingApplicationException.notFound("Order not found: " + orderId));
        TradeOrder finished = orderRepository.save(order.finish(Instant.now()));

        ListingReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> TradingApplicationException.notFound("Reservation not found: " + reservationId));
        reservationRepository.save(reservation.commit());

        orchestrator.transitionState(sagaId, OrderSagaState.EXECUTING, OrderSagaState.COMPLETED,
                OrderSagaStep.FINALIZE_ORDER, "order " + finished.orderCode() + " settled");

        outbox.append(new OrderSettledEvent(finished.id(), finished.orderCode(), finished.listingId(),
                finished.batchId(), finished.buyerCompanyId(), finished.sellerCompanyId(), finished.quantity(),
                finished.grossAmount().amount(), finished.platformFee().amount()));
        log.info("Order {} finished, {} credits moved to buyer company {}, gross {} fee {}",
                finished.orderCode(), finished.quantity(), finished.buyerCompanyId(),
                finished.grossAmount().amount(), finished.platformFee().amount());
    }

    /** Compensation of RESERVE/SETTLE/TRANSFER: cancel the order, its reservation and give the quantity back. */
    @Transactional
    public void cancelOrderAndReleaseListing(String idempotencyKey, Long orderId, Long listingId, long quantity,
                                             String reason) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> TradingApplicationException.notFound("Order not found: " + orderId));
        if (order.status() == com.carbonx.trading.domain.order.OrderStatus.CANCELLED) {
            log.info("Order {} is already cancelled; skipping duplicate compensation", order.orderCode());
            return;
        }
        orderRepository.save(order.cancel());
        reservationRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(reservation -> reservationRepository.save(reservation.cancel()));

        MarketListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> TradingApplicationException.notFound("Listing not found: " + listingId));
        listingRepository.save(listing.restore(quantity));
        log.info("Compensated order {}: listing {} got {} credits back ({})",
                order.orderCode(), listingId, quantity, reason);
    }

    /** Terminates the saga at COMPENSATED and publishes OrderCompensatedEvent in the same commit. */
    @Transactional
    public void markCompensated(String sagaId, Long orderId, Long listingId, OrderSagaStep failedStep,
                                String reason) {
        orchestrator.transitionState(sagaId, OrderSagaState.COMPENSATING, OrderSagaState.COMPENSATED,
                failedStep, reason);
        String orderCode = orderRepository.findById(orderId).map(TradeOrder::orderCode).orElse(null);
        outbox.append(new OrderCompensatedEvent(sagaId, orderId, orderCode, listingId, failedStep.name(), reason));
    }

    /** Resolves an order's reserved quantity for scheduled timeout recovery. */
    @Transactional(readOnly = true)
    public Long findOrderQuantity(Long orderId) {
        return orderRepository.findById(orderId).map(TradeOrder::quantity).orElse(null);
    }

    /** Completes a saga whose remote settlement and credit transfer already succeeded before crash. */
    @Transactional
    public void finalizeOrderByKey(String sagaId, Long orderId, String idempotencyKey) {
        ListingReservation reservation = reservationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> TradingApplicationException.notFound(
                        "Reservation not found for key: " + idempotencyKey));
        finalizeOrder(sagaId, orderId, reservation.id());
    }

    private static String nextOrderCode() {
        return "ORD-" + LocalDate.now().format(ORDER_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
