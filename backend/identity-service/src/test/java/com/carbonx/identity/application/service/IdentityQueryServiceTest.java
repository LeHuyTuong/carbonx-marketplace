package com.carbonx.identity.application.service;

import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.exception.IdentityErrorCode;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.CompanyView;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.VehicleView;
import com.carbonx.identity.application.port.out.CompanyRepositoryPort;
import com.carbonx.identity.application.port.out.KycProfileRepositoryPort;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.application.port.out.VehicleRepositoryPort;
import com.carbonx.identity.domain.company.Company;
import com.carbonx.identity.domain.company.KycStatus;
import com.carbonx.identity.domain.vehicle.PlateNumber;
import com.carbonx.identity.domain.vehicle.Vehicle;
import com.carbonx.identity.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IdentityQueryServiceTest {

    @Mock
    private CompanyRepositoryPort companyRepository;
    @Mock
    private VehicleRepositoryPort vehicleRepository;
    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private KycProfileRepositoryPort kycProfileRepository;

    @InjectMocks
    private IdentityQueryService service;

    @Test
    @DisplayName("an unknown company is a NOT_FOUND application error")
    void getCompanyByIdRejectsUnknownCompany() {
        given(companyRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompanyById(404L))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("the company projection exposes the KYC status without leaking the entity")
    void getCompanyByIdMapsToView() {
        given(companyRepository.findById(3L)).willReturn(Optional.of(Company.rehydrate(3L, 8L,
                "Green Fleet JSC", "0312345678", "HCM", KycStatus.APPROVED, null, null)));

        CompanyView view = service.getCompanyById(3L);

        assertThat(view.companyId()).isEqualTo(3L);
        assertThat(view.businessName()).isEqualTo("Green Fleet JSC");
        assertThat(view.kycStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("plate verification normalises and de-duplicates the requested plates")
    void listVehiclesByPlatesNormalisesInput() {
        given(vehicleRepository.findByCompanyIdAndPlateNumbers(3L, List.of("51A12345", "30A99999")))
                .willReturn(List.of(vehicle(99L, "51A12345")));

        List<VehicleView> views = service.listVehiclesByPlates(3L,
                List.of("51A-123.45", "51a 12345", "30A-999.99"));

        assertThat(views).extracting(VehicleView::plateNumber).containsExactly("51A12345");
    }

    @Test
    @DisplayName("plate verification with no plate numbers never hits the database")
    void listVehiclesByPlatesWithEmptyInput() {
        assertThat(service.listVehiclesByPlates(3L, List.of())).isEmpty();
    }

    @Test
    @DisplayName("admin pagination is clamped to a positive page and at most 100 rows")
    void listAllVehiclesForAdminClampsPagination() {
        given(vehicleRepository.findAll(0, 100)).willReturn(List.of());

        assertThat(service.listAllVehiclesForAdmin(-3, 5_000)).isEmpty();
    }

    private static Vehicle vehicle(Long id, String plate) {
        return Vehicle.rehydrate(id, 7L, 3L, new PlateNumber(plate), "VinFast", "VF8", null,
                VehicleStatus.ACTIVE, null, null);
    }
}
