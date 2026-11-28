package com.carbonx.registry.api;

import com.carbonx.registry.api.dto.RegistryDtos.BatchResponse;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provenance lookup used by trading, ledger and the BFFs: which batch, which serial range and how
 * many credits came out of one approved emission report.
 */
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Registry — Credit Batches")
public class BatchController {

    private final RegistryQueryUseCase registryQueryUseCase;

    @GetMapping("/by-source-report/{sourceReportId}")
    @PreAuthorize("hasAnyRole('COMPANY','CVA','ADMIN')")
    @Operation(summary = "Batch and serial provenance of an approved emission report (Redis cached)")
    public BatchResponse findBySourceReport(@PathVariable Long sourceReportId) {
        return BatchResponse.from(registryQueryUseCase.getBatchBySourceReportId(sourceReportId));
    }
}
