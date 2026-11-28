package com.carbonx.ledger.application.port.out;

import com.carbonx.ledger.domain.wallet.Withdrawal;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRepositoryPort {
    Optional<Withdrawal> findById(Long id);
    List<Withdrawal> findByUserIdOrdered(Long userId);
    List<Withdrawal> findAllOrdered();
    Withdrawal save(Withdrawal withdrawal);
}
