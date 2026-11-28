package com.carbonx.registry.application.port.out;

import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchProvenanceView;

import java.util.Optional;

/**
 * Read-through cache of batch provenance. Provenance is read on every order, retirement and
 * dashboard call but changes only when a batch is issued, so it lives in Redis for a short TTL.
 * A cache outage must degrade to the database, never fail the query.
 */
public interface BatchProvenanceCachePort {

    Optional<BatchProvenanceView> get(Long sourceReportId);

    void put(BatchProvenanceView view);

    void evict(Long sourceReportId);
}
