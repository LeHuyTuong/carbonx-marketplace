package com.carbonx.registry.support;

import com.carbonx.common.domain.DomainEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchProvenanceView;
import com.carbonx.registry.application.port.out.BatchProvenanceCachePort;
import com.carbonx.registry.application.port.out.CreditBatchRepositoryPort;
import com.carbonx.registry.application.port.out.CreditHoldingRepositoryPort;
import com.carbonx.registry.application.port.out.CreditMovementRepositoryPort;
import com.carbonx.registry.application.port.out.RetirementCertificateRepositoryPort;
import com.carbonx.registry.application.port.out.StaticReferenceCachePort;
import com.carbonx.registry.application.port.out.StaticReferenceCachePort.ProjectMethodologyRef;
import com.carbonx.registry.domain.credit.CreditBatch;
import com.carbonx.registry.domain.credit.CreditHolding;
import com.carbonx.registry.domain.credit.CreditMovement;
import com.carbonx.registry.domain.credit.MovementType;
import com.carbonx.registry.domain.credit.RetirementCertificate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateful in-memory registry: one store plus one adapter per out-port. The build host has no
 * Docker, so the 100 -> 80/20 scenario runs through the real application services against these
 * fakes, keeping the serial ledger and ownership arithmetic under test without a database.
 */
public class InMemoryRegistry {

    private final Map<Long, CreditBatch> batches = new LinkedHashMap<>();
    private final Map<Long, CreditHolding> holdings = new LinkedHashMap<>();
    private final List<CreditMovement> movementLog = new ArrayList<>();
    private final Map<Long, RetirementCertificate> certificates = new LinkedHashMap<>();
    private final List<DomainEvent> outbox = new ArrayList<>();
    private final Map<Long, BatchProvenanceView> provenance = new HashMap<>();
    private final Map<Long, ProjectMethodologyRef> methodologies = new HashMap<>();

    private long batchSequence;
    private long holdingSequence;
    private long movementSequence;
    private long certificateSequence;
    private int sourceReportLookups;

    public final CreditBatchRepositoryPort batchPort = new BatchPort();
    public final CreditHoldingRepositoryPort holdingPort = new HoldingPort();
    public final CreditMovementRepositoryPort movementPort = new MovementPort();
    public final RetirementCertificateRepositoryPort certificatePort = new CertificatePort();
    public final BatchProvenanceCachePort provenancePort = new ProvenancePort();
    public final StaticReferenceCachePort staticReferencePort = new StaticReferencePort();
    public final OutboxPort outboxPort = new OutboxAdapter();

    // ------------------------------------------------------------------ CreditBatchRepositoryPort

    private final class BatchPort implements CreditBatchRepositoryPort {

        @Override
        public CreditBatch insert(CreditBatch batch) {
            if (batch.id() != null) {
                throw new IllegalStateException("credit_batches is append-only");
            }
            CreditBatch saved = batch.withId(++batchSequence);
            batches.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<CreditBatch> findById(Long id) {
            return Optional.ofNullable(batches.get(id));
        }

        @Override
        public List<CreditBatch> findAllByIds(Collection<Long> ids) {
            return ids.stream().map(batches::get).filter(Objects::nonNull).toList();
        }

        @Override
        public List<CreditBatch> findAllOrderedDesc() {
            return batches.values().stream()
                    .sorted(Comparator.comparingLong(CreditBatch::id).reversed())
                    .toList();
        }

        @Override
        public Optional<CreditBatch> findBySourceReportId(Long sourceReportId) {
            sourceReportLookups++;
            return batches.values().stream().filter(batch -> batch.belongsToReport(sourceReportId)).findFirst();
        }

        @Override
        public long highestAllocatedSerial(Long projectId, Long issuerCompanyId, int vintageYear) {
            return inScope(projectId, issuerCompanyId, vintageYear)
                    .mapToLong(CreditBatch::serialEnd)
                    .max()
                    .orElse(0L);
        }

        @Override
        public Optional<BigDecimal> findResidualOfLatestBatch(Long projectId, Long issuerCompanyId, int vintageYear) {
            return inScope(projectId, issuerCompanyId, vintageYear)
                    .max(Comparator.comparingLong(CreditBatch::serialEnd))
                    .map(CreditBatch::residualTco2e);
        }

        private java.util.stream.Stream<CreditBatch> inScope(Long projectId, Long issuerCompanyId, int vintageYear) {
            return batches.values().stream()
                    .filter(batch -> batch.projectId().equals(projectId)
                            && batch.issuerCompanyId().equals(issuerCompanyId)
                            && batch.vintageYear() == vintageYear);
        }
    }

    // ----------------------------------------------------------------- CreditHoldingRepositoryPort

    private final class HoldingPort implements CreditHoldingRepositoryPort {

        @Override
        public CreditHolding save(CreditHolding holding) {
            CreditHolding saved = holding.id() == null
                    ? new CreditHolding(++holdingSequence, holding.batchId(), holding.holderCompanyId(),
                            holding.availableQty(), holding.lockedQty(), holding.retiredQty(),
                            holding.serialStart(), holding.serialEnd(), holding.version())
                    : holding;
            holdings.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<CreditHolding> findById(Long id) {
            return Optional.ofNullable(holdings.get(id));
        }

        @Override
        public Optional<CreditHolding> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<CreditHolding> findByHolderCompanyId(Long companyId) {
            return holdings.values().stream()
                    .filter(holding -> holding.holderCompanyId().equals(companyId))
                    .sorted(Comparator.comparingLong(CreditHolding::id))
                    .toList();
        }
    }

    // ---------------------------------------------------------------- CreditMovementRepositoryPort

    private final class MovementPort implements CreditMovementRepositoryPort {

        @Override
        public CreditMovement save(CreditMovement movement) {
            CreditMovement saved = new CreditMovement(++movementSequence, movement.holdingId(),
                    movement.batchId(), movement.movementType(), movement.quantity(), movement.serialStart(),
                    movement.serialEnd(), movement.referenceId(), movement.createdAt());
            movementLog.add(saved);
            return saved;
        }

        @Override
        public Optional<CreditMovement> findFirstByReferenceIdAndType(String referenceId, MovementType type) {
            return movementLog.stream()
                    .filter(movement -> movement.movementType() == type)
                    .filter(movement -> referenceId != null && referenceId.equals(movement.referenceId()))
                    .findFirst();
        }

        @Override
        public List<CreditMovement> findByHoldingId(Long holdingId) {
            return movementLog.stream().filter(movement -> movement.holdingId().equals(holdingId)).toList();
        }
    }

    // -------------------------------------------------------- RetirementCertificateRepositoryPort

    private final class CertificatePort implements RetirementCertificateRepositoryPort {

        @Override
        public RetirementCertificate save(RetirementCertificate certificate) {
            RetirementCertificate saved = new RetirementCertificate(++certificateSequence,
                    certificate.certificateCode(), certificate.holdingId(), certificate.batchId(),
                    certificate.holderCompanyId(), certificate.retiredQty(), certificate.serialStart(),
                    certificate.serialEnd(), certificate.beneficiaryName(), certificate.reason(),
                    certificate.pdfUrl(), certificate.retiredAt());
            certificates.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<RetirementCertificate> findById(Long id) {
            return Optional.ofNullable(certificates.get(id));
        }
    }

    // -------------------------------------------------------------- BatchProvenanceCachePort

    private final class ProvenancePort implements BatchProvenanceCachePort {

        @Override
        public Optional<BatchProvenanceView> get(Long sourceReportId) {
            return Optional.ofNullable(provenance.get(sourceReportId));
        }

        @Override
        public void put(BatchProvenanceView view) {
            provenance.put(view.sourceReportId(), view);
        }

        @Override
        public void evict(Long sourceReportId) {
            provenance.remove(sourceReportId);
        }
    }

    // --------------------------------------------------------------- StaticReferenceCachePort

    private final class StaticReferencePort implements StaticReferenceCachePort {

        @Override
        public Optional<ProjectMethodologyRef> getProjectMethodology(Long projectId) {
            return Optional.ofNullable(methodologies.get(projectId));
        }

        @Override
        public void putProjectMethodology(ProjectMethodologyRef ref) {
            methodologies.put(ref.projectId(), ref);
        }
    }

    // ------------------------------------------------------------------------------- OutboxPort

    private final class OutboxAdapter implements OutboxPort {

        @Override
        public void append(DomainEvent event) {
            outbox.add(event);
        }
    }

    // ------------------------------------------------------------------------ test accessors

    public CreditBatch batch(Long id) {
        return batches.get(id);
    }

    public Collection<CreditBatch> allBatches() {
        return List.copyOf(batches.values());
    }

    public CreditHolding holding(Long id) {
        return holdings.get(id);
    }

    public Collection<CreditHolding> allHoldings() {
        return List.copyOf(holdings.values());
    }

    public List<CreditMovement> movements() {
        return List.copyOf(movementLog);
    }

    public List<CreditMovement> movementsOf(Long holdingId) {
        return movementPort.findByHoldingId(holdingId);
    }

    public RetirementCertificate certificate(Long id) {
        return certificates.get(id);
    }

    public List<DomainEvent> outboxEvents() {
        return List.copyOf(outbox);
    }

    public Map<Long, BatchProvenanceView> cachedProvenance() {
        return Map.copyOf(provenance);
    }

    public Map<Long, ProjectMethodologyRef> methodologies() {
        return Map.copyOf(methodologies);
    }

    public int sourceReportLookups() {
        return sourceReportLookups;
    }
}
