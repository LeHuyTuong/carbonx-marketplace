package com.carbonx.identity.application.port.out;

import com.carbonx.identity.domain.kyc.KycProfile;

import java.util.List;
import java.util.Optional;

/** Outbound persistence port for EV owner KYC dossiers. */
public interface KycProfileRepositoryPort {
    Optional<KycProfile> findByEvOwnerId(Long evOwnerId);

    List<KycProfile> findAllOrdered();

    KycProfile save(KycProfile profile);
}
