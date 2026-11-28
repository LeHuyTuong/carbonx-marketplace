package com.carbonx.identity.infrastructure.persistence.adapter;

import com.carbonx.identity.application.port.out.CompanyRepositoryPort;
import com.carbonx.identity.domain.company.Company;
import com.carbonx.identity.domain.company.KycStatus;
import com.carbonx.identity.infrastructure.persistence.entity.CompanyJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the Company aggregate to the {@code companies} table. */
@Component
@RequiredArgsConstructor
public class CompanyPersistenceAdapter implements CompanyRepositoryPort {

    private final CompanyJpaRepository repository;

    @Override
    public Optional<Company> findById(Long id) {
        return repository.findById(id).map(CompanyPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Company> findByUserId(Long userId) {
        return repository.findByUserId(userId).map(CompanyPersistenceAdapter::toDomain);
    }

    @Override
    public List<Company> findAllApproved() {
        return repository.findByKycStatusOrderByBusinessNameAsc(KycStatus.APPROVED.name()).stream()
                .map(CompanyPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Company save(Company company) {
        return toDomain(repository.save(toEntity(company)));
    }

    @Override
    public List<Company> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream().map(CompanyPersistenceAdapter::toDomain).toList();
    }

    private static Company toDomain(CompanyJpaEntity entity) {
        return Company.rehydrate(entity.getId(), entity.getUserId(), entity.getBusinessName(),
                entity.getTaxCode(), entity.getBusinessLicense(), entity.getAddress(),
                KycStatus.valueOf(entity.getKycStatus()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static CompanyJpaEntity toEntity(Company company) {
        return CompanyJpaEntity.builder()
                .id(company.id())
                .userId(company.userId())
                .businessName(company.businessName())
                .taxCode(company.taxCode())
                .businessLicense(company.businessLicense())
                .address(company.address())
                .kycStatus(company.kycStatus().name())
                .build();
    }
}
