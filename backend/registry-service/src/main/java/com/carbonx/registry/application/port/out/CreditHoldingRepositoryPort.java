package com.carbonx.registry.application.port.out;

import com.carbonx.registry.domain.credit.CreditHolding;

import java.util.List;
import java.util.Optional;

/** Persistence port of {@code credit_holdings} (the ownership split of a batch). */
public interface CreditHoldingRepositoryPort {

    CreditHolding save(CreditHolding holding);

    Optional<CreditHolding> findById(Long id);

    /**
     * Loads the holding with a {@code SELECT ... FOR UPDATE} row lock. Every credit operation goes
     * through this method so concurrent locks, transfers and retirements serialise per holding
     * instead of overdrawing it.
     */
    Optional<CreditHolding> findByIdForUpdate(Long id);

    List<CreditHolding> findByHolderCompanyId(Long companyId);
}
