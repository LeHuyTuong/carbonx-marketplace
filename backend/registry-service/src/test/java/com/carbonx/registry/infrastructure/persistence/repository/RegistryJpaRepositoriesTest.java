package com.carbonx.registry.infrastructure.persistence.repository;

import com.carbonx.registry.domain.credit.MovementType;
import com.carbonx.registry.infrastructure.persistence.entity.CreditMovementJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every Spring Data JPA repository query derivation against real JPA metamodel and H2,
 * catching property-name mismatches (such as BUG-1128-REGISTRY) at build time.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:registry_jpa_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RegistryJpaRepositoriesTest {

    @Autowired
    private CreditBatchJpaRepository batchRepository;

    @Autowired
    private CreditHoldingJpaRepository holdingRepository;

    @Autowired
    private CreditMovementJpaRepository movementRepository;

    @Autowired
    private RetirementCertificateJpaRepository certificateRepository;

    @Autowired
    private OutboxEventJpaRepository outboxRepository;

    @Test
    void movementRepositoryQueriesMatchMovementTypePropertyAndPersistCleanly() {
        assertThat(batchRepository.count()).isZero();
        assertThat(holdingRepository.count()).isZero();
        assertThat(certificateRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();

        CreditMovementJpaEntity saved = movementRepository.save(CreditMovementJpaEntity.builder()
                .holdingId(1L)
                .batchId(10L)
                .movementType(MovementType.LOCK.name())
                .quantity(25L)
                .serialStart(1L)
                .serialEnd(25L)
                .referenceId("idem-lock-1")
                .build());

        assertThat(movementRepository.findFirstByReferenceIdAndMovementTypeOrderByIdAsc(
                "idem-lock-1", MovementType.LOCK.name()))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(saved.getId());
                    assertThat(found.getQuantity()).isEqualTo(25L);
                });
    }
}
