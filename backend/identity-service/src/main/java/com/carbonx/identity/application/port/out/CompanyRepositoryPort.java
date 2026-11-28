package com.carbonx.identity.application.port.out;

import com.carbonx.identity.domain.company.Company;

import java.util.List;
import java.util.Optional;

/** Outbound persistence port for the Company aggregate. */
public interface CompanyRepositoryPort {
    Optional<Company> findById(Long id);

    Optional<Company> findByUserId(Long userId);

    List<Company> findAllApproved();

    List<Company> findAllOrdered();

    Company save(Company company);
}
