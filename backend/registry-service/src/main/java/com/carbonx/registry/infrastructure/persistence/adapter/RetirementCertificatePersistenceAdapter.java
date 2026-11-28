package com.carbonx.registry.infrastructure.persistence.adapter;

import com.carbonx.registry.application.port.out.RetirementCertificateRepositoryPort;
import com.carbonx.registry.domain.credit.RetirementCertificate;
import com.carbonx.registry.infrastructure.persistence.entity.RetirementCertificateJpaEntity;
import com.carbonx.registry.infrastructure.persistence.repository.RetirementCertificateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapts retirement proofs to {@code retirement_certificates}. */
@Component
@RequiredArgsConstructor
public class RetirementCertificatePersistenceAdapter implements RetirementCertificateRepositoryPort {

    private final RetirementCertificateJpaRepository repository;

    @Override
    public RetirementCertificate save(RetirementCertificate certificate) {
        return toDomain(repository.save(RetirementCertificateJpaEntity.builder()
                .certificateCode(certificate.certificateCode())
                .holdingId(certificate.holdingId())
                .batchId(certificate.batchId())
                .holderCompanyId(certificate.holderCompanyId())
                .retiredQty(certificate.retiredQty())
                .serialStart(certificate.serialStart())
                .serialEnd(certificate.serialEnd())
                .beneficiaryName(certificate.beneficiaryName())
                .reason(certificate.reason())
                .pdfUrl(certificate.pdfUrl())
                .retiredAt(certificate.retiredAt())
                .build()));
    }

    @Override
    public Optional<RetirementCertificate> findById(Long id) {
        return repository.findById(id).map(RetirementCertificatePersistenceAdapter::toDomain);
    }

    private static RetirementCertificate toDomain(RetirementCertificateJpaEntity entity) {
        return new RetirementCertificate(entity.getId(), entity.getCertificateCode(), entity.getHoldingId(),
                entity.getBatchId(), entity.getHolderCompanyId(), entity.getRetiredQty(), entity.getSerialStart(),
                entity.getSerialEnd(), entity.getBeneficiaryName(), entity.getReason(), entity.getPdfUrl(),
                entity.getRetiredAt());
    }
}
