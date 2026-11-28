package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.PaymentDetailsRepositoryPort;
import com.carbonx.ledger.domain.wallet.PaymentDetails;
import com.carbonx.ledger.infrastructure.persistence.entity.PaymentDetailsJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.PaymentDetailsJpaRepository;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentDetailsPersistenceAdapter implements PaymentDetailsRepositoryPort {

    private final PaymentDetailsJpaRepository repository;
    private final WalletJpaRepository walletRepository;

    @Override
    public Optional<PaymentDetails> findByUserId(Long userId) {
        return walletRepository.findByOwnerUserId(userId)
                .flatMap(wallet -> repository.findByWalletId(wallet.getId()))
                .map(e -> toDomain(e, userId));
    }

    @Override
    public PaymentDetails save(PaymentDetails details) {
        WalletJpaEntity wallet = walletRepository.findByOwnerUserId(details.userId())
                .orElseGet(() -> walletRepository.save(WalletJpaEntity.builder()
                        .ownerUserId(details.userId())
                        .balanceUsd(BigDecimal.ZERO)
                        .version(0L)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()));
        Instant now = Instant.now();
        PaymentDetailsJpaEntity existing = repository.findByWalletId(wallet.getId()).orElse(null);
        PaymentDetailsJpaEntity saved = repository.save(PaymentDetailsJpaEntity.builder()
                .id(existing != null ? existing.getId() : details.id())
                .walletId(wallet.getId())
                .bankName(details.bankName())
                .branch(details.branch())
                .accountNumber(details.accountNumber())
                .accountHolder(details.accountHolder())
                .createdAt(existing != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build());
        return toDomain(saved, details.userId());
    }

    private static PaymentDetails toDomain(PaymentDetailsJpaEntity e, Long userId) {
        return new PaymentDetails(e.getId(), userId, e.getBankName(), e.getAccountNumber(),
                e.getAccountHolder(), e.getBranch(), e.getUpdatedAt());
    }
}
