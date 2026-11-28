package com.carbonx.ledger.domain.shared;

import java.text.MessageFormat;

public class LedgerDomainException extends RuntimeException {

    private final LedgerDomainErrorCode errorCode;
    private final Object[] args;

    public LedgerDomainException(LedgerDomainErrorCode errorCode, Object... args) {
        super(MessageFormat.format(errorCode.defaultMessage(), args));
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public LedgerDomainErrorCode errorCode() {
        return errorCode;
    }

    public String messageKey() {
        return errorCode.messageKey();
    }

    public Object[] args() {
        return args.clone();
    }

    public static LedgerDomainException required(String fieldName) {
        return new LedgerDomainException(LedgerDomainErrorCode.FIELD_REQUIRED, fieldName);
    }
}
