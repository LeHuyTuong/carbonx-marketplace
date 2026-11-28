package com.carbonx.trading.application.exception;

/** Application-level failure carrying a stable error code for the HTTP layer. */
public class TradingApplicationException extends RuntimeException {

    private final TradingErrorCode code;

    public TradingApplicationException(TradingErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public TradingApplicationException(TradingErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TradingErrorCode code() {
        return code;
    }

    public static TradingApplicationException badRequest(String message) {
        return new TradingApplicationException(TradingErrorCode.BAD_REQUEST, message);
    }

    public static TradingApplicationException notFound(String message) {
        return new TradingApplicationException(TradingErrorCode.NOT_FOUND, message);
    }

    public static TradingApplicationException forbidden(String message) {
        return new TradingApplicationException(TradingErrorCode.FORBIDDEN, message);
    }

    public static TradingApplicationException conflict(String message) {
        return new TradingApplicationException(TradingErrorCode.CONFLICT, message);
    }

    public static TradingApplicationException listingUnavailable(String message) {
        return new TradingApplicationException(TradingErrorCode.LISTING_UNAVAILABLE, message);
    }

    public static TradingApplicationException sagaInProgress(String sagaId) {
        return new TradingApplicationException(TradingErrorCode.SAGA_IN_PROGRESS,
                "Order Saga " + sagaId + " is still running; retry the same idempotency key shortly");
    }

    public static TradingApplicationException sagaCompensated(String orderCode, String failedStep, String reason) {
        return new TradingApplicationException(TradingErrorCode.SAGA_COMPENSATED,
                "Order " + orderCode + " was rolled back at step " + failedStep + ": " + reason);
    }

    public static TradingApplicationException unavailable(String dependency, Throwable cause) {
        return new TradingApplicationException(TradingErrorCode.SERVICE_UNAVAILABLE,
                dependency + " is unavailable, the Order Saga step can be retried with the same idempotency key",
                cause);
    }
}
