package com.carbonx.identity.infrastructure.persistence.adapter;

import com.carbonx.identity.application.port.out.KycProfileRepositoryPort;
import com.carbonx.identity.domain.company.KycStatus;
import com.carbonx.identity.domain.kyc.KycProfile;
import com.carbonx.identity.infrastructure.persistence.entity.KycProfileJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.KycProfileJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the EV owner KYC profile to the {@code kyc_profiles} table. */
@Component
@RequiredArgsConstructor
public class KycProfilePersistenceAdapter implements KycProfileRepositoryPort {

    private final KycProfileJpaRepository repository;

    @Override
    public Optional<KycProfile> findByEvOwnerId(Long evOwnerId) {
        return repository.findByEvOwnerId(evOwnerId).map(KycProfilePersistenceAdapter::toDomain);
    }

    @Override
    public KycProfile save(KycProfile profile) {
        return toDomain(repository.save(toEntity(profile)));
    }

    @Override
    public List<KycProfile> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream().map(KycProfilePersistenceAdapter::toDomain).toList();
    }

    private static KycProfile toDomain(KycProfileJpaEntity entity) {
        return KycProfile.rehydrate(entity.getId(), entity.getEvOwnerId(), entity.getFullName(),
                entity.getDocumentType(), entity.getDocumentNumber(), entity.getPhone(), entity.getCountry(),
                entity.getAddress(), entity.getBirthDate(), entity.getGender(),
                KycStatus.valueOf(entity.getKycStatus()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static KycProfileJpaEntity toEntity(KycProfile profile) {
        return KycProfileJpaEntity.builder()
                .id(profile.id())
                .evOwnerId(profile.evOwnerId())
                .fullName(profile.fullName())
                .documentType(profile.documentType())
                .documentNumber(profile.documentNumber())
                .phone(profile.phone())
                .country(profile.country())
                .address(profile.address())
                .birthDate(profile.birthDate())
                .gender(profile.gender())
                .kycStatus(profile.kycStatus().name())
                .build();
    }
}
