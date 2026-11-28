package com.carbonx.ledger.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.out.PaymentDetailsRepositoryPort;
import com.carbonx.ledger.application.port.out.ProfitDistributionRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletTransactionRepositoryPort;
import com.carbonx.ledger.application.port.out.WithdrawalRepositoryPort;
import com.carbonx.ledger.domain.wallet.ProfitDistribution;
import com.carbonx.ledger.domain.wallet.Wallet;
import com.carbonx.ledger.domain.wallet.WalletTransaction;
import com.carbonx.ledger.domain.wallet.Withdrawal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Read-only side of the Ledger CQRS split. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LedgerQueryService implements LedgerQueryUseCase {

    private final WalletRepositoryPort walletRepository;
    private final WalletTransactionRepositoryPort txRepository;
    private final PaymentDetailsRepositoryPort paymentDetailsRepository;
    private final WithdrawalRepositoryPort withdrawalRepository;
    private final ProfitDistributionRepositoryPort distributionRepository;

    @Override
    @Transactional
    public WalletView getOrCreateWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(Wallet.createForUser(userId)));
        return new WalletView(wallet.id(), wallet.userId(), wallet.balance(), wallet.currency(), wallet.updatedAt());
    }

    @Override
    public List<WalletTransactionView> listTransactions(Long userId, boolean isAdmin) {
        List<WalletTransaction> txs = isAdmin
                ? txRepository.findAllOrdered()
                : txRepository.findByUserIdOrdered(userId);
        return txs.stream()
                .map(t -> new WalletTransactionView(t.id(), t.walletId(), t.userId(), t.type().name(),
                        t.amount(), t.balanceAfter(), t.orderRef(), t.description(), t.createdAt()))
                .toList();
    }

    @Override
    public long countAllTransactions() {
        return txRepository.countAll();
    }

    @Override
    public PaymentDetailsView getPaymentDetails(Long userId) {
        return paymentDetailsRepository.findByUserId(userId)
                .map(d -> new PaymentDetailsView(d.id(), d.userId(), d.bankName(), d.accountNumber(),
                        d.accountHolder(), d.branch(), d.updatedAt()))
                .orElse(null);
    }

    @Override
    public List<WithdrawalView> listMyWithdrawals(Long userId) {
        return withdrawalRepository.findByUserIdOrdered(userId).stream()
                .map(LedgerQueryService::toWithdrawalView)
                .toList();
    }

    @Override
    public List<WithdrawalView> listAllWithdrawals() {
        return withdrawalRepository.findAllOrdered().stream()
                .map(LedgerQueryService::toWithdrawalView)
                .toList();
    }

    @Override
    public List<ProfitDistributionView> listDistributions(Long companyId, boolean isAdmin) {
        List<ProfitDistribution> items = isAdmin
                ? distributionRepository.findAllOrdered()
                : distributionRepository.findByCompanyIdOrdered(companyId);
        return items.stream()
                .map(d -> new ProfitDistributionView(d.id(), d.companyId(), d.sourceReportId(),
                        d.totalAmount(), d.recipientCount(), d.status(), d.createdAt()))
                .toList();
    }

    @Override
    public PayoutProvenanceView getTransactionProvenance(Long userId, Long walletTransactionId) {
        WalletTransaction tx = txRepository.findById(walletTransactionId)
                .orElseThrow(() -> LedgerApplicationException.notFound("Transaction not found: " + walletTransactionId));
        if (!tx.userId().equals(userId)) {
            throw LedgerApplicationException.forbidden("Transaction does not belong to caller");
        }
        return new PayoutProvenanceView(tx.id(), null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, Money.of(tx.amount()), tx.createdAt());
    }

    @Override
    public byte[] exportDistributionExcel(Long companyId, Long distributionId) {
        return new byte[0];
    }

    private static WithdrawalView toWithdrawalView(Withdrawal w) {
        return new WithdrawalView(w.id(), w.userId(), w.amount(), w.status().name(),
                w.bankName(), w.accountNumber(), w.accountHolder(),
                w.processedBy(), w.processedAt(), w.createdAt());
    }
}
