package com.carbonx.ledger.application.exception;

public class LedgerApplicationException extends RuntimeException {

    private final LedgerErrorCode code;

    public LedgerApplicationException(LedgerErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LedgerErrorCode code() {
        return code;
    }

    public static LedgerApplicationException notFound(String message) {
        return new LedgerApplicationException(LedgerErrorCode.NOT_FOUND, message);
    }

    public static LedgerApplicationException badRequest(String message) {
        return new LedgerApplicationException(LedgerErrorCode.BAD_REQUEST, message);
    }

    public static LedgerApplicationException forbidden(String message) {
        return new LedgerApplicationException(LedgerErrorCode.FORBIDDEN, message);
    }

    public static LedgerApplicationException insufficientFunds(String message) {
        return new LedgerApplicationException(LedgerErrorCode.INSUFFICIENT_FUNDS, message);
    }
}
