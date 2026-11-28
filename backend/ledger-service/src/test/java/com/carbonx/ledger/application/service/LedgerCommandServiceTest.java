package com.carbonx.ledger.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.application.exception.LedgerErrorCode;
import com.carbonx.ledger.application.port.out.PaymentDetailsRepositoryPort;
import com.carbonx.ledger.application.port.out.ProfitDistributionRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletRepositoryPort;
import com.carbonx.ledger.application.port.out.WalletTransactionRepositoryPort;
import com.carbonx.ledger.application.port.out.WithdrawalRepositoryPort;
import com.carbonx.ledger.domain.wallet.TransactionType;
import com.carbonx.ledger.domain.wallet.Wallet;
import com.carbonx.ledger.domain.wallet.WalletTransaction;
import com.carbonx.ledger.domain.wallet.Withdrawal;
import com.carbonx.ledger.domain.wallet.WithdrawalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LedgerCommandServiceTest {

    @Mock
    private WalletRepositoryPort walletRepository;
    @Mock
    private WalletTransactionRepositoryPort txRepository;
    @Mock
    private PaymentDetailsRepositoryPort paymentDetailsRepository;
    @Mock
    private WithdrawalRepositoryPort withdrawalRepository;
    @Mock
    private ProfitDistributionRepositoryPort distributionRepository;

    private LedgerCommandService service;

    @BeforeEach
    void setUp() {
        service = new LedgerCommandService(walletRepository, txRepository,
                paymentDetailsRepository, withdrawalRepository, distributionRepository);
    }

    @Test
    void settleOrderPaymentDebitsBuyerCreditsSellerMinusFeeAndIsIdempotent() {
        Wallet buyer = new Wallet(1L, 10L, new BigDecimal("100.0000"), "USD", 0L, Instant.now(), Instant.now());
        Wallet seller = new Wallet(2L, 20L, new BigDecimal("10.0000"), "USD", 0L, Instant.now(), Instant.now());

        given(txRepository.findByIdempotencyKey("k1:debit")).willReturn(Optional.empty());
        given(walletRepository.findByUserIdForUpdate(10L)).willReturn(Optional.of(buyer));
        given(walletRepository.findByUserIdForUpdate(20L)).willReturn(Optional.of(seller));
        given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));
        given(txRepository.save(any(WalletTransaction.class))).willAnswer(inv -> inv.getArgument(0));

        service.settleOrderPayment("k1", 99L, 10L, 20L,
                Money.of(new BigDecimal("40.00")), Money.of(new BigDecimal("2.00")));

        then(walletRepository).should().save(argThat(w -> w.userId().equals(10L)
                && w.balance().compareTo(new BigDecimal("60.0000")) == 0));
        then(walletRepository).should().save(argThat(w -> w.userId().equals(20L)
                && w.balance().compareTo(new BigDecimal("48.0000")) == 0));
        then(txRepository).should(times(2)).save(any(WalletTransaction.class));

        // Replayed call with same idempotencyKey must be a no-op
        given(txRepository.findByIdempotencyKey("k1:debit"))
                .willReturn(Optional.of(WalletTransaction.record(buyer, TransactionType.ORDER_DEBIT,
                        new BigDecimal("40.0000"), 99L, "k1:debit", "Order #99")));
        service.settleOrderPayment("k1", 99L, 10L, 20L,
                Money.of(new BigDecimal("40.00")), Money.of(new BigDecimal("2.00")));
        then(walletRepository).should(times(2)).save(any(Wallet.class));
    }

    @Test
    void settleOrderPaymentRejectsInsufficientFunds() {
        Wallet buyer = new Wallet(1L, 10L, new BigDecimal("5.0000"), "USD", 0L, Instant.now(), Instant.now());
        given(txRepository.findByIdempotencyKey("k2:debit")).willReturn(Optional.empty());
        given(walletRepository.findByUserIdForUpdate(10L)).willReturn(Optional.of(buyer));

        assertThatThrownBy(() -> service.settleOrderPayment("k2", 100L, 10L, 20L,
                Money.of(new BigDecimal("50.00")), Money.of(BigDecimal.ZERO)))
                .isInstanceOf(LedgerApplicationException.class)
                .extracting(ex -> ((LedgerApplicationException) ex).code())
                .isEqualTo(LedgerErrorCode.INSUFFICIENT_FUNDS);
        then(walletRepository).should(never()).save(any());
    }

    @Test
    void rejectingWithdrawalRefundsUserWallet() {
        Withdrawal pending = new Withdrawal(5L, 10L, new BigDecimal("25.0000"),
                WithdrawalStatus.PENDING, "VCB", "123", "User", null, null, Instant.now());
        Wallet userWallet = new Wallet(1L, 10L, new BigDecimal("10.0000"), "USD", 0L, Instant.now(), Instant.now());

        given(withdrawalRepository.findById(5L)).willReturn(Optional.of(pending));
        given(withdrawalRepository.save(any(Withdrawal.class))).willAnswer(inv -> inv.getArgument(0));
        given(walletRepository.findByUserIdForUpdate(10L)).willReturn(Optional.of(userWallet));
        given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));
        given(txRepository.save(any(WalletTransaction.class))).willAnswer(inv -> inv.getArgument(0));

        service.processWithdrawal(1L, 5L, false);

        then(walletRepository).should().save(argThat(w -> w.userId().equals(10L)
                && w.balance().compareTo(new BigDecimal("35.0000")) == 0));
    }
}
