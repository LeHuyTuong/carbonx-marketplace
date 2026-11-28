package com.carbonx.trading.domain.order;

/** Lifecycle of a market listing; only LISTED listings can be bought. */
public enum ListingStatus {
    LISTED,
    SOLD_OUT,
    CANCELLED,
    EXPIRED;

    public boolean isPurchasable() {
        return this == LISTED;
    }
}
