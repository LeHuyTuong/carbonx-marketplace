package com.carbonx.ledger.application.port.in;

import com.carbonx.common.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Guarantees full EV Owner payout traceability back to CreditBatch ID, batchCode, serialRange & sourceReportId. */
public interface LedgerQueryUseCase {
    WalletView getOrCreateWallet(Long userId);
    List<WalletTransactionView> listTransactions(Long userId, boolean isAdmin);
    long countAllTransactions();
    PaymentDetailsView getPaymentDetails(Long userId);
    List<WithdrawalView> listMyWithdrawals(Long userId);
    List<WithdrawalView> listAllWithdrawals();
    List<ProfitDistributionView> listDistributions(Long companyId, boolean isAdmin);
    PayoutProvenanceView getTransactionProvenance(Long userId, Long walletTransactionId);
    byte[] exportDistributionExcel(Long companyId, Long distributionId);

    record WalletView(Long id, Long userId, BigDecimal balance, String currency, Instant updatedAt) {}
    record WalletTransactionView(Long id, Long walletId, Long userId, String type, BigDecimal amount,
                                 BigDecimal balanceAfter, Long orderRef, String description, Instant createdAt) {}
    record PaymentDetailsView(Long id, Long userId, String bankName, String accountNumber,
                                  String accountHolder, String branch, Instant updatedAt) {}
    record WithdrawalView(Long id, Long userId, BigDecimal amount, String status, String bankName,
                              String accountNumber, String accountHolder, Long processedBy,
                              Instant processedAt, Instant createdAt) {}
    record ProfitDistributionView(Long id, Long companyId, Long sourceReportId, BigDecimal totalAmount,
                                      int recipientCount, String status, Instant createdAt) {}
    record PayoutProvenanceView(Long walletTransactionId, Long distributionId, Long sourceReportId, Long creditBatchId, String batchCode, String serialRange, String period, BigDecimal kwhContributed, BigDecimal allocatedCredits, Money amountUsd, Instant paidAt) {}
}
