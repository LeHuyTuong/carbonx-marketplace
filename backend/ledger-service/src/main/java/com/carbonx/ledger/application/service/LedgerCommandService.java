package com.carbonx.ledger.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.out.PaymentDetailsRepositoryPort;
import com.carbonx.ledger.application.port.out.ProfitDistributionRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletTransactionRepositoryPort;
import com.carbonx.ledger.application.port.out.WithdrawalRepositoryPort;
import com.carbonx.ledger.domain.shared.LedgerDomainException;
import com.carbonx.ledger.domain.wallet.PaymentDetails;
import com.carbonx.ledger.domain.wallet.ProfitDistribution;
import com.carbonx.ledger.domain.wallet.TransactionType;
import com.carbonx.ledger.domain.wallet.Wallet;
import com.carbonx.ledger.domain.wallet.WalletTransaction;
import com.carbonx.ledger.domain.wallet.Withdrawal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Command side of the Ledger CQRS split: all mutations run inside local ACID transactions. */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LedgerCommandService implements LedgerCommandUseCase {

    private final WalletRepositoryPort walletRepository;
    private final WalletTransactionRepositoryPort txRepository;
    private final PaymentDetailsRepositoryPort paymentDetailsRepository;
    private final WithdrawalRepositoryPort withdrawalRepository;
    private final ProfitDistributionRepositoryPort distributionRepository;

    @Override
    public void settleOrderPayment(String idempotencyKey, Long orderRef, Long buyerUserId, Long sellerUserId,
                                   Money gross, Money fee) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw LedgerApplicationException.badRequest("idempotencyKey is required");
        }
        String debitKey = idempotencyKey + ":debit";
        if (txRepository.findByIdempotencyKey(debitKey).isPresent()) {
            log.info("Order {} settlement already processed for key {}", orderRef, idempotencyKey);
            return;
        }
        BigDecimal grossAmount = gross.amount();
        BigDecimal feeAmount = fee == null ? BigDecimal.ZERO : fee.amount();
        BigDecimal netSellerAmount = grossAmount.subtract(feeAmount);
        if (netSellerAmount.signum() < 0) {
            throw LedgerApplicationException.badRequest("Platform fee cannot exceed gross amount");
        }

        Wallet buyerWallet = lockOrCreateWallet(buyerUserId);
        Wallet debitedBuyer;
        try {
            debitedBuyer = walletRepository.save(buyerWallet.debit(grossAmount));
        } catch (LedgerDomainException ex) {
            throw LedgerApplicationException.insufficientFunds(ex.getMessage());
        }
        txRepository.save(WalletTransaction.record(debitedBuyer, TransactionType.ORDER_DEBIT, grossAmount,
                orderRef, debitKey, "Order #" + orderRef + " payment"));

        if (sellerUserId != null && netSellerAmount.signum() > 0) {
            Wallet sellerWallet = lockOrCreateWallet(sellerUserId);
            Wallet creditedSeller = walletRepository.save(sellerWallet.credit(netSellerAmount));
            txRepository.save(WalletTransaction.record(creditedSeller, TransactionType.ORDER_CREDIT,
                    netSellerAmount, orderRef, idempotencyKey + ":credit",
                    "Order #" + orderRef + " proceeds (fee=" + feeAmount + ")"));
        }
        log.info("Settled order {} buyer={} seller={} gross={} fee={}",
                orderRef, buyerUserId, sellerUserId, grossAmount, feeAmount);
    }

    @Override
    public void compensateOrderPayment(String idempotencyKey, Long orderRef, String reason) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw LedgerApplicationException.badRequest("idempotencyKey is required");
        }
        String refundKey = idempotencyKey + ":refund";
        if (txRepository.findByIdempotencyKey(refundKey).isPresent()) {
            return;
        }
        List<WalletTransaction> existing = txRepository.findByOrderRef(orderRef);
        Optional<WalletTransaction> debitTx = existing.stream()
                .filter(t -> t.type() == TransactionType.ORDER_DEBIT)
                .findFirst();
        if (debitTx.isEmpty()) {
            log.info("No debit found for order {} compensation; skipping", orderRef);
            return;
        }
        WalletTransaction originalDebit = debitTx.get();
        Wallet buyerWallet = lockOrCreateWallet(originalDebit.userId());
        Wallet refundedBuyer = walletRepository.save(buyerWallet.credit(originalDebit.amount()));
        txRepository.save(WalletTransaction.record(refundedBuyer, TransactionType.ORDER_REFUND,
                originalDebit.amount(), orderRef, refundKey,
                "Refund order #" + orderRef + ": " + (reason == null ? "compensated" : reason)));

        existing.stream()
                .filter(t -> t.type() == TransactionType.ORDER_CREDIT)
                .findFirst()
                .ifPresent(creditTx -> {
                    Wallet sellerWallet = lockOrCreateWallet(creditTx.userId());
                    BigDecimal clawback = creditTx.amount().min(sellerWallet.balance());
                    if (clawback.signum() > 0) {
                        Wallet updatedSeller = walletRepository.save(sellerWallet.debit(clawback));
                        txRepository.save(WalletTransaction.record(updatedSeller, TransactionType.ORDER_REFUND,
                                clawback, orderRef, refundKey + ":seller",
                                "Reversal for compensated order #" + orderRef));
                    }
                });
    }

    @Override
    public String initiateDeposit(Long userId, Money amount, String provider) {
        Wallet wallet = lockOrCreateWallet(userId);
        Wallet updated = walletRepository.save(wallet.credit(amount.amount()));
        String txRef = "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        txRepository.save(WalletTransaction.record(updated, TransactionType.DEPOSIT, amount.amount(),
                null, txRef, "Deposit via " + (provider == null ? "SANDBOX" : provider)));
        return txRef;
    }

    @Override
    public void handleGatewayWebhook(String provider, String payload, String signature) {
        log.info("Received payment webhook from provider={} length={}",
                provider, payload == null ? 0 : payload.length());
    }

    @Override
    public void savePaymentDetails(Long userId, String bankName, String accountNumber,
                                   String accountHolder, String branch) {
        Optional<PaymentDetails> existing = paymentDetailsRepository.findByUserId(userId);
        PaymentDetails details = new PaymentDetails(
                existing.map(PaymentDetails::id).orElse(null),
                userId, bankName, accountNumber, accountHolder, branch, Instant.now());
        paymentDetailsRepository.save(details);
    }

    @Override
    public Long requestWithdrawal(Long userId, Long bankAccountId, Money amount) {
        Wallet wallet = lockOrCreateWallet(userId);
        Wallet debited;
        try {
            debited = walletRepository.save(wallet.debit(amount.amount()));
        } catch (LedgerDomainException ex) {
            throw LedgerApplicationException.insufficientFunds(ex.getMessage());
        }
        PaymentDetails bank = paymentDetailsRepository.findByUserId(userId).orElse(null);
        Withdrawal saved = withdrawalRepository.save(Withdrawal.request(userId, amount.amount(), bank));
        txRepository.save(WalletTransaction.record(debited, TransactionType.WITHDRAWAL, amount.amount(),
                null, "WD-" + saved.id(), "Withdrawal request #" + saved.id()));
        return saved.id();
    }

    @Override
    public void processWithdrawal(Long adminId, Long withdrawalId, boolean approved) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> LedgerApplicationException.notFound("Withdrawal not found: " + withdrawalId));
        Withdrawal decided;
        try {
            decided = withdrawalRepository.save(withdrawal.decide(adminId, approved));
        } catch (LedgerDomainException ex) {
            throw LedgerApplicationException.badRequest(ex.getMessage());
        }
        if (!approved) {
            Wallet wallet = lockOrCreateWallet(decided.userId());
            Wallet refunded = walletRepository.save(wallet.credit(decided.amount()));
            txRepository.save(WalletTransaction.record(refunded, TransactionType.WITHDRAWAL_REFUND,
                    decided.amount(), null, "WD-REFUND-" + decided.id(),
                    "Refund rejected withdrawal #" + decided.id()));
        }
    }

    @Override
    public Long executeProfitDistribution(Long companyId, Long sourceReportId, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? "DIST-" + companyId + "-" + sourceReportId
                : idempotencyKey.trim();
        Optional<ProfitDistribution> existing = distributionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        ProfitDistribution saved = distributionRepository.save(new ProfitDistribution(
                null, companyId, sourceReportId, key, BigDecimal.ZERO, 0, "COMPLETED", Instant.now()));
        return saved.id();
    }

    private Wallet lockOrCreateWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.createForUser(userId)));
    }
}
