package com.carbonx.identity.api.dto;

import com.carbonx.identity.application.port.in.IdentityQueryUseCase.CompanyView;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.VehicleView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Vehicle registration, listing and inter-service plate verification contracts. */
public final class VehicleDtos {

    private VehicleDtos() {
    }

    public record VehicleRegistrationRequest(
            @NotNull Long companyId,
            @NotBlank @Size(max = 32) String plateNumber,
            @NotBlank @Size(max = 64) String brand,
            @NotBlank @Size(max = 64) String model,
            @Size(max = 500) String documentUrl) {
    }

    public record VehicleRegisteredResponse(Long vehicleId) {
    }

    public record VehicleResponse(Long id, Long evOwnerId, Long companyId, String plateNumber,
                                  String brand, String model, String status) {
        public static VehicleResponse from(VehicleView view) {
            return new VehicleResponse(view.id(), view.evOwnerId(), view.companyId(), view.plateNumber(),
                    view.brand(), view.model(), view.status());
        }
    }

    public record CompanySummaryResponse(Long companyId, Long userId, String businessName, String taxCode,
                                         String kycStatus) {
        public static CompanySummaryResponse from(CompanyView view) {
            return new CompanySummaryResponse(view.companyId(), view.userId(), view.businessName(),
                    view.taxCode(), view.kycStatus());
        }
    }

    public record PlateVerificationRequest(
            @NotNull Long companyId,
            @NotEmpty List<@NotBlank @Size(max = 32) String> plateNumbers) {
    }

    public record PlateVerificationResponse(Long companyId, List<String> verifiedPlates) {
    }
}
