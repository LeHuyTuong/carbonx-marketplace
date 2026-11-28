package com.carbonx.ledger.application.port.in;

import com.carbonx.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Inbound Use-Case Port: Wallet, Immutable WalletTransactions, Withdrawals & Payout Provenance.
 * Guarantees EV Owners can trace every payout credit back to exact CreditBatch ID, batchCode, serialRange & reportId.
 */
public interface WalletUseCase {
    WalletOverviewView getMyWallet(Long userId);

    DepositInitView initiateDeposit(Long userId, InitiateDepositCommand command);

    void handlePaymentCallback(String provider, String rawPayload, String signatureHeader);

    BankAccountView saveBankAccount(Long userId, SaveBankAccountCommand command);

    WithdrawalView requestWithdrawal(Long userId, RequestWithdrawalCommand command);

    WithdrawalView processWithdrawalByAdmin(Long adminUserId, Long withdrawalId, boolean approved);

    List<WithdrawalView> listWithdrawalsForAdmin(String statusFilter);

    PayoutProvenanceView getTransactionProvenance(Long userId, Long walletTransactionId);

    record InitiateDepositCommand(Money amount, String paymentMethod) {} // VNPAY, STRIPE, PAYPAL

    record SaveBankAccountCommand(String bankName, String branch, String accountNumber, String accountHolder) {}

    record RequestWithdrawalCommand(Long bankAccountId, Money amount) {}

    record DepositInitView(String paymentUrl, String externalRef) {}

    record WalletOverviewView(Long walletId, Money balance, BankAccountView bankAccount, List<WalletTransactionView> recentTransactions) {}

    record WalletTransactionView(Long id, String txType, Money amount, Money balanceBefore, Money balanceAfter, String referenceType, String referenceId, String description, Instant createdAt) {}

    record BankAccountView(Long id, String bankName, String branch, String accountNumber, String accountHolder) {}

    record WithdrawalView(Long id, Long walletId, Money amount, String bankName, String accountNumber, String accountHolder, String status, Instant requestedAt, Instant processedAt) {}

    record PayoutProvenanceView(
            Long walletTransactionId,
            Long distributionId,
            Long sourceReportId,
            Long creditBatchId,
            String batchCode,
            String serialRange,
            String period,
            BigDecimal kwhContributed,
            BigDecimal allocatedCredits,
            Money amountUsd,
            Instant paidAt
    ) {}
}
