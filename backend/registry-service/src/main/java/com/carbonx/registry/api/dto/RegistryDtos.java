package com.carbonx.registry.api.dto;

import com.carbonx.registry.application.port.in.RegistryCommandUseCase.LockResult;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase.TransferResult;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchProvenanceView;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.HoldingView;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.MovementView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Holding, retirement and provenance request/response contracts. */
public final class RegistryDtos {

    private RegistryDtos() {
    }

    public record LockRequest(
            @NotBlank @Size(max = 100) String idempotencyKey,
            @NotNull @Positive Long sellerCompanyId,
            @Positive long quantity,
            @Size(max = 128) String listingRef) {
    }

    public record ReleaseRequest(
            @NotBlank @Size(max = 100) String idempotencyKey,
            @Positive long quantity,
            @Size(max = 255) String reason) {
    }

    public record TransferRequest(
            @NotBlank @Size(max = 100) String idempotencyKey,
            @NotNull @Positive Long buyerCompanyId,
            @Positive long quantity,
            @Size(max = 128) String orderRef) {
    }

    public record RetireRequest(
            @Positive long quantity,
            @Size(max = 255) String beneficiaryName,
            @Size(max = 500) String reason) {
    }

    public record IdResponse(Long id) {
    }

    public record LockResponse(Long holdingId, Long batchId, String batchCode, String projectTitle, long lockedQty) {
        public static LockResponse from(LockResult result) {
            return new LockResponse(result.holdingId(), result.batchId(), result.batchCode(),
                    result.projectTitle(), result.lockedQty());
        }
    }

    public record TransferResponse(Long buyerHoldingId, Long batchId, long serialStart, long serialEnd) {
        public static TransferResponse from(TransferResult result) {
            return new TransferResponse(result.buyerHoldingId(), result.batchId(), result.serialStart(),
                    result.serialEnd());
        }
    }

    public record HoldingResponse(Long holdingId, Long batchId, String batchCode, String projectTitle,
                                  long availableQty, long lockedQty, long retiredQty, String serialRange) {
        public static HoldingResponse from(HoldingView view) {
            return new HoldingResponse(view.holdingId(), view.batchId(), view.batchCode(), view.projectTitle(),
                    view.availableQty(), view.lockedQty(), view.retiredQty(), view.serialRange());
        }
    }

    public record MovementResponse(Long id, String movementType, long quantity, String serialRange,
                                   String referenceId, Instant createdAt) {
        public static MovementResponse from(MovementView view) {
            return new MovementResponse(view.id(), view.movementType(), view.quantity(), view.serialRange(),
                    view.referenceId(), view.createdAt());
        }
    }

    public record BatchResponse(Long batchId, String batchCode, Long sourceReportId, String serialRange,
                                long totalIssuedQty) {
        public static BatchResponse from(BatchProvenanceView view) {
            return new BatchResponse(view.batchId(), view.batchCode(), view.sourceReportId(),
                    view.serialRange(), view.totalIssuedQty());
        }
    }

    public static List<HoldingResponse> holdings(List<HoldingView> views) {
        return views.stream().map(HoldingResponse::from).toList();
    }
}
