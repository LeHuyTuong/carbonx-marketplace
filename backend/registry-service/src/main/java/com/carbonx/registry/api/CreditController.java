package com.carbonx.registry.api;

import com.carbonx.registry.api.dto.RegistryDtos.IdResponse;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchDetailView;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.CompanyBatchView;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.HoldingView;
import com.carbonx.registry.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade over {@link RegistryQueryUseCase} & {@link RegistryCommandUseCase} serving
 * the credit-batch & retirement endpoints expected by the Web SPA.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Registry — Credit Batches & Portfolio")
public class CreditController {

    private final RegistryQueryUseCase registryQueryUseCase;
    private final RegistryCommandUseCase registryCommandUseCase;

    @GetMapping("/credits/batches")
    @PreAuthorize("hasAnyRole('COMPANY','CVA','ADMIN')")
    @Operation(summary = "List all issued credit batches for CVA/Admin/Company inspection")
    public List<BatchDetailView> listAllBatches() {
        return registryQueryUseCase.listAllBatches();
    }

    @GetMapping("/credits/batches/{batchId}")
    @PreAuthorize("hasAnyRole('COMPANY','CVA','ADMIN')")
    @Operation(summary = "Get batch details by batch id")
    public BatchDetailView getBatchById(@PathVariable Long batchId) {
        return registryQueryUseCase.listAllBatches().stream()
                .filter(b -> batchId.equals(b.id()))
                .findFirst()
                .orElseThrow(() -> RegistryApplicationException.notFound("Batch not found: " + batchId));
    }

    @GetMapping("/credits/preview/{reportId}")
    @PreAuthorize("hasAnyRole('CVA','ADMIN')")
    @Operation(summary = "Lookup issued batch provenance for an approved report")
    public BatchDetailView previewByReport(@PathVariable Long reportId) {
        return registryQueryUseCase.listAllBatches().stream()
                .filter(b -> reportId.equals(b.reportId()))
                .findFirst()
                .orElseThrow(() -> RegistryApplicationException.notFound(
                        "No issued batch yet for report " + reportId));
    }

    @GetMapping("/my/credits/batches")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List credit holdings/batches owned by the current company")
    public List<CompanyBatchView> listMyBatches(@AuthenticationPrincipal AuthenticatedUser principal) {
        return registryQueryUseCase.listCompanyBatches(PrincipalGuard.require(principal).effectiveCompanyId());
    }

    @GetMapping("/my/credits")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List credit holdings (including retired summary) for the current company")
    public List<MyCreditSummary> listMyCredits(@AuthenticationPrincipal AuthenticatedUser principal) {
        Long companyId = PrincipalGuard.require(principal).effectiveCompanyId();
        List<CompanyBatchView> batches = registryQueryUseCase.listCompanyBatches(companyId);
        return batches.stream()
                .map(b -> new MyCreditSummary(
                        b.holdingId(),
                        b.batchCode(),
                        b.projectTitle(),
                        b.retiredQty() > 0 ? b.retiredQty() : b.availableQty(),
                        b.retiredQty() > 0 ? "RETIRED" : b.status(),
                        b.issuedAt()))
                .toList();
    }

    @PostMapping("/my/credits/retire")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Retire credits by holdingId or batchCode")
    @SuppressWarnings("unchecked")
    public IdResponse retireFromCompany(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @RequestBody Map<String, Object> body) {
        Long companyId = PrincipalGuard.require(principal).effectiveCompanyId();
        Map<String, Object> payload = body != null && body.get("data") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : (body == null ? Map.of() : body);

        Long holdingId = asLong(payload.get("holdingId"));
        String batchCode = payload.get("batchCode") instanceof String s ? s.trim() : null;
        Long quantity = asLong(payload.get("quantity"));
        String beneficiary = payload.get("beneficiaryName") instanceof String s && !s.isBlank()
                ? s.trim() : "Company Self-Retirement";
        String reason = payload.get("reason") instanceof String s && !s.isBlank()
                ? s.trim() : "Carbon offset retirement";

        if (holdingId == null && batchCode != null && !batchCode.isBlank()) {
            holdingId = registryQueryUseCase.listCompanyHoldings(companyId).stream()
                    .filter(h -> batchCode.equals(h.batchCode()))
                    .map(HoldingView::holdingId)
                    .findFirst()
                    .orElseThrow(() -> RegistryApplicationException.notFound(
                            "No holding found for batchCode: " + batchCode));
        }
        if (holdingId == null || quantity == null || quantity <= 0) {
            throw RegistryApplicationException.badRequest("holdingId (or batchCode) and positive quantity are required");
        }
        Long certificateId = registryCommandUseCase.retireCredits(companyId, holdingId, quantity, beneficiary, reason);
        return new IdResponse(certificateId);
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record MyCreditSummary(Long id, String creditCode, String name, long carbonCredit,
                                  String status, java.time.Instant issuedAt) {}
}
