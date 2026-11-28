package com.carbonx.ledger.application.port.out;

import com.carbonx.ledger.domain.wallet.ProfitDistribution;

import java.util.List;
import java.util.Optional;

public interface ProfitDistributionRepositoryPort {
    Optional<ProfitDistribution> findByIdempotencyKey(String idempotencyKey);
    List<ProfitDistribution> findByCompanyIdOrdered(Long companyId);
    List<ProfitDistribution> findAllOrdered();
    ProfitDistribution save(ProfitDistribution distribution);
}
