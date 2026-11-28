package com.carbonx.identity.api;

import com.carbonx.identity.api.dto.VehicleDtos.CompanySummaryResponse;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Approved company directory consumed by EV owners when they pick a project operator. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Identity — Company directory")
public class CompanyDirectoryController {

    private final IdentityQueryUseCase identityQueryUseCase;

    @GetMapping("/companies")
    @Operation(summary = "List companies whose KYC dossier has been approved")
    public List<CompanySummaryResponse> listApprovedCompanies() {
        return identityQueryUseCase.listApprovedCompanies().stream()
                .map(CompanySummaryResponse::from)
                .toList();
    }
}
