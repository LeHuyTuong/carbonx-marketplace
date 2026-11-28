package com.carbonx.registry.infrastructure.persistence.repository;

import com.carbonx.registry.infrastructure.persistence.entity.CreditBatchJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CreditBatchJpaRepository extends JpaRepository<CreditBatchJpaEntity, Long> {

    Optional<CreditBatchJpaEntity> findBySourceReportId(Long sourceReportId);

    List<CreditBatchJpaEntity> findByIdIn(Collection<Long> ids);

    List<CreditBatchJpaEntity> findAllByOrderByIdDesc();

    Optional<CreditBatchJpaEntity> findTopByProjectIdAndIssuerCompanyIdAndVintageYearOrderBySerialEndDesc(
            Long projectId, Long issuerCompanyId, int vintageYear);

    /**
     * Locks every batch of the serial scope so two concurrent issuances for the same
     * project/company/vintage serialise their serial allocation instead of minting duplicates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CreditBatchJpaEntity b"
            + " where b.projectId = :projectId and b.issuerCompanyId = :issuerCompanyId"
            + " and b.vintageYear = :vintageYear order by b.serialEnd desc")
    List<CreditBatchJpaEntity> lockScopeOrderBySerialEndDesc(@Param("projectId") Long projectId,
                                                             @Param("issuerCompanyId") Long issuerCompanyId,
                                                             @Param("vintageYear") int vintageYear);
}
