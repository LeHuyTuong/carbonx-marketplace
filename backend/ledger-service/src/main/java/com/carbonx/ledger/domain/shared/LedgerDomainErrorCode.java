package com.carbonx.ledger.domain.shared;

/**
 * Stable domain error codes and i18n message keys. Keeping keys in the domain allows the API
 * exception handler to resolve localized messages via {@code MessageSource} without coupling
 * domain invariants to a single language.
 */
public enum LedgerDomainErrorCode {
    FIELD_REQUIRED("LEDGER_FIELD_REQUIRED", "ledger.validation.required", "{0} is required"),
    AMOUNT_NOT_POSITIVE("LEDGER_AMOUNT_NOT_POSITIVE", "ledger.validation.amount_positive", "Amount must be greater than zero"),
    NEGATIVE_BALANCE("LEDGER_NEGATIVE_BALANCE", "ledger.wallet.negative_balance", "Wallet balance cannot be negative"),
    INSUFFICIENT_FUNDS("LEDGER_INSUFFICIENT_FUNDS", "ledger.wallet.insufficient_funds", "Insufficient wallet balance"),
    INVALID_WITHDRAWAL_STATE("LEDGER_INVALID_WITHDRAWAL_STATE", "ledger.withdrawal.invalid_state", "Withdrawal {0} is already {1}");

    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    LedgerDomainErrorCode(String code, String messageKey, String defaultMessage) {
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
