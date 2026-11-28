package com.carbonx.ledger.domain.wallet;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    WITHDRAWAL_REFUND,
    ORDER_DEBIT,
    ORDER_CREDIT,
    ORDER_REFUND,
    PROFIT_SHARE,
    PLATFORM_FEE
}
