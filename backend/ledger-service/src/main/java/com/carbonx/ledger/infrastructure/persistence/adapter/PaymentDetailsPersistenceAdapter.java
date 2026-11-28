package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.PaymentDetailsRepositoryPort;
import com.carbonx.ledger.domain.wallet.PaymentDetails;
import com.carbonx.ledger.infrastructure.persistence.entity.PaymentDetailsJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.PaymentDetailsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentDetailsPersistenceAdapter implements PaymentDetailsRepositoryPort {

    private final PaymentDetailsJpaRepository repository;

    @Override
    public Optional<PaymentDetails> findByUserId(Long userId) {
        return repository.findByUserId(userId).map(PaymentDetailsPersistenceAdapter::toDomain);
    }

    @Override
    public PaymentDetails save(PaymentDetails details) {
        PaymentDetailsJpaEntity saved = repository.save(PaymentDetailsJpaEntity.builder()
                .id(details.id())
                .userId(details.userId())
                .bankName(details.bankName())
                .accountNumber(details.accountNumber())
                .accountHolder(details.accountHolder())
                .branch(details.branch())
                .updatedAt(details.updatedAt())
                .build());
        return toDomain(saved);
    }

    private static PaymentDetails toDomain(PaymentDetailsJpaEntity e) {
        return new PaymentDetails(e.getId(), e.getUserId(), e.getBankName(), e.getAccountNumber(),
                e.getAccountHolder(), e.getBranch(), e.getUpdatedAt());
    }
}
