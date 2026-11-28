package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.application.port.out.WithdrawalRepositoryPort;
import com.carbonx.ledger.domain.wallet.Withdrawal;
import com.carbonx.ledger.domain.wallet.WithdrawalStatus;
import com.carbonx.ledger.infrastructure.persistence.entity.PaymentDetailsJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.entity.WithdrawalJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.PaymentDetailsJpaRepository;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletJpaRepository;
import com.carbonx.ledger.infrastructure.persistence.repository.WithdrawalJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WithdrawalPersistenceAdapter implements WithdrawalRepositoryPort {

    private final WithdrawalJpaRepository repository;
    private final WalletJpaRepository walletRepository;
    private final PaymentDetailsJpaRepository bankRepository;

    @Override
    public Optional<Withdrawal> findById(Long id) {
        return repository.findById(id).map(this::toDomainSingle);
    }

    @Override
    public List<Withdrawal> findByUserIdOrdered(Long userId) {
        return walletRepository.findByOwnerUserId(userId)
                .map(wallet -> {
                    PaymentDetailsJpaEntity bank = bankRepository.findByWalletId(wallet.getId()).orElse(null);
                    return repository.findByWalletIdOrderByIdDesc(wallet.getId()).stream()
                            .map(e -> toDomain(e, userId, bank))
                            .toList();
                })
                .orElse(List.of());
    }

    @Override
    public List<Withdrawal> findAllOrdered() {
        List<WithdrawalJpaEntity> rows = repository.findAllByOrderByIdDesc();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> owners = walletRepository.findAllById(
                        rows.stream().map(WithdrawalJpaEntity::getWalletId).distinct().toList()).stream()
                .collect(Collectors.toMap(WalletJpaEntity::getId, WalletJpaEntity::getOwnerUserId, (a, b) -> a));
        Map<Long, PaymentDetailsJpaEntity> banks = bankRepository.findAllById(
                        rows.stream().map(WithdrawalJpaEntity::getBankAccountId).distinct().toList()).stream()
                .collect(Collectors.toMap(PaymentDetailsJpaEntity::getId, b -> b, (a, b) -> a));
        return rows.stream()
                .map(e -> toDomain(e, owners.get(e.getWalletId()), banks.get(e.getBankAccountId())))
                .toList();
    }

    @Override
    public Withdrawal save(Withdrawal w) {
        WalletJpaEntity wallet = walletRepository.findByOwnerUserId(w.userId())
                .orElseThrow(() -> LedgerApplicationException.notFound("Wallet not found for user: " + w.userId()));
        PaymentDetailsJpaEntity bank = bankRepository.findByWalletId(wallet.getId())
                .orElseThrow(() -> LedgerApplicationException.badRequest(
                        "Payment details must be configured before requesting a withdrawal"));
        WithdrawalJpaEntity saved = repository.save(WithdrawalJpaEntity.builder()
                .id(w.id())
                .walletId(wallet.getId())
                .bankAccountId(bank.getId())
                .amountUsd(w.amount())
                .status(w.status().name())
                .processedByAdmin(w.processedBy())
                .requestedAt(w.createdAt())
                .processedAt(w.processedAt())
                .build());
        return toDomain(saved, w.userId(), bank);
    }

    private Withdrawal toDomainSingle(WithdrawalJpaEntity e) {
        Long userId = walletRepository.findById(e.getWalletId()).map(WalletJpaEntity::getOwnerUserId).orElse(null);
        PaymentDetailsJpaEntity bank = bankRepository.findById(e.getBankAccountId()).orElse(null);
        return toDomain(e, userId, bank);
    }

    private static Withdrawal toDomain(WithdrawalJpaEntity e, Long userId, PaymentDetailsJpaEntity bank) {
        return new Withdrawal(e.getId(), userId, e.getAmountUsd(),
                WithdrawalStatus.valueOf(e.getStatus()),
                bank != null ? bank.getBankName() : null,
                bank != null ? bank.getAccountNumber() : null,
                bank != null ? bank.getAccountHolder() : null,
                e.getProcessedByAdmin(), e.getProcessedAt(), e.getRequestedAt());
    }
}
