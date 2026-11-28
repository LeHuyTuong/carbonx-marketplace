package com.carbonx.registry.application.port.out;

import com.carbonx.registry.domain.credit.RetirementCertificate;

import java.util.Optional;

/** Persistence port of {@code retirement_certificates}. */
public interface RetirementCertificateRepositoryPort {

    RetirementCertificate save(RetirementCertificate certificate);

    Optional<RetirementCertificate> findById(Long id);
}
