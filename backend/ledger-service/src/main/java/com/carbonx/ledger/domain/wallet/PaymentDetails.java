package com.carbonx.ledger.domain.wallet;

import com.carbonx.ledger.domain.shared.LedgerDomainException;

import java.time.Instant;

public record PaymentDetails(
        Long id,
        Long userId,
        String bankName,
        String accountNumber,
        String accountHolder,
        String branch,
        Instant updatedAt
) {
    public PaymentDetails {
        if (userId == null) {
            throw LedgerDomainException.required("userId");
        }
        if (bankName == null || bankName.isBlank()) {
            throw LedgerDomainException.required("bankName");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw LedgerDomainException.required("accountNumber");
        }
        if (accountHolder == null || accountHolder.isBlank()) {
            throw LedgerDomainException.required("accountHolder");
        }
        bankName = bankName.trim();
        accountNumber = accountNumber.trim();
        accountHolder = accountHolder.trim();
        branch = branch == null || branch.isBlank() ? null : branch.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
