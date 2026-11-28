package com.carbonx.trading.domain.shared;

/** The listing cannot fulfil the requested quantity (sold out, cancelled, expired or over-reserved). */
public class ListingUnavailableException extends RuntimeException {

    public ListingUnavailableException(String message) {
        super(message);
    }
}
