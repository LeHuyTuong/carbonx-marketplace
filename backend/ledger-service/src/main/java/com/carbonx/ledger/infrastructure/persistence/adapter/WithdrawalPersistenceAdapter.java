package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.WithdrawalRepositoryPort;
import com.carbonx.ledger.domain.wallet.Withdrawal;
import com.carbonx.ledger.domain.wallet.WithdrawalStatus;
import com.carbonx.ledger.infrastructure.persistence.entity.WithdrawalJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.WithdrawalJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WithdrawalPersistenceAdapter implements WithdrawalRepositoryPort {

    private final WithdrawalJpaRepository repository;

    @Override
    public Optional<Withdrawal> findById(Long id) {
        return repository.findById(id).map(WithdrawalPersistenceAdapter::toDomain);
    }

    @Override
    public List<Withdrawal> findByUserIdOrdered(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream()
                .map(WithdrawalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Withdrawal> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(WithdrawalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Withdrawal save(Withdrawal w) {
        WithdrawalJpaEntity saved = repository.save(WithdrawalJpaEntity.builder()
                .id(w.id())
                .userId(w.userId())
                .amount(w.amount())
                .status(w.status().name())
                .bankName(w.bankName())
                .accountNumber(w.accountNumber())
                .accountHolder(w.accountHolder())
                .processedBy(w.processedBy())
                .processedAt(w.processedAt())
                .createdAt(w.createdAt())
                .build());
        return toDomain(saved);
    }

    private static Withdrawal toDomain(WithdrawalJpaEntity e) {
        return new Withdrawal(e.getId(), e.getUserId(), e.getAmount(),
                WithdrawalStatus.valueOf(e.getStatus()), e.getBankName(), e.getAccountNumber(),
                e.getAccountHolder(), e.getProcessedBy(), e.getProcessedAt(), e.getCreatedAt());
    }
}
