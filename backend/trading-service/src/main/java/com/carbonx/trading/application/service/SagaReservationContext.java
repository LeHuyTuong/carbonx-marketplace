package com.carbonx.trading.application.service;

import com.carbonx.common.domain.Money;

/** What the saga engine needs after the first local transaction committed the reservation. */
public record SagaReservationContext(
        String sagaId,
        Long orderId,
        String orderCode,
        Long reservationId,
        Long listingId,
        Long holdingId,
        Long sellerCompanyId,
        Long sellerUserId,
        long quantity,
        Money grossAmount,
        Money platformFee
) {
    public SagaReservationContext(
            String sagaId,
            Long orderId,
            String orderCode,
            Long reservationId,
            Long listingId,
            Long holdingId,
            Long sellerCompanyId,
            long quantity,
            Money grossAmount,
            Money platformFee
    ) {
        this(sagaId, orderId, orderCode, reservationId, listingId, holdingId, sellerCompanyId, sellerCompanyId,
                quantity, grossAmount, platformFee);
    }

    public Long effectiveSellerUserId() {
        return sellerUserId != null ? sellerUserId : sellerCompanyId;
    }
}
