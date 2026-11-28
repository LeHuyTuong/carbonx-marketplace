package com.carbonx.registry.api;

import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.HoldingView;
import com.carbonx.registry.application.security.AuthenticatedUser;
import com.carbonx.registry.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.registry.infrastructure.security.JwtTokenVerifier;
import com.carbonx.registry.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC gate of the registry: credit ownership is a COMPANY concern, so an EV owner, a CVA and an
 * anonymous caller must never reach a holding endpoint even if the frontend offers it.
 */
@WebMvcTest(controllers = HoldingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HoldingAccessRbacTest {

    private static final String COMPANY_TOKEN = "company-token";
    private static final String EV_OWNER_TOKEN = "ev-owner-token";
    private static final String CVA_TOKEN = "cva-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistryQueryUseCase registryQueryUseCase;

    @MockBean
    private RegistryCommandUseCase registryCommandUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @BeforeEach
    void stubTokens() {
        given(jwtTokenVerifier.verify(COMPANY_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "company@carbonx.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify(EV_OWNER_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(9L, "owner@carbonx.vn", "ROLE_EV_OWNER")));
        given(jwtTokenVerifier.verify(CVA_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(2L, "cva@carbonx.vn", "ROLE_CVA")));
    }

    @Test
    @DisplayName("EV_OWNER calling GET /api/v1/holdings is rejected with 403")
    void evOwnerCannotReadHoldings() throws Exception {
        mockMvc.perform(get("/api/v1/holdings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN))
                .andExpect(status().isForbidden());

        then(registryQueryUseCase).should(never()).listCompanyHoldings(anyLong());
    }

    @Test
    @DisplayName("CVA calling GET /api/v1/holdings is rejected with 403")
    void cvaCannotReadHoldings() throws Exception {
        mockMvc.perform(get("/api/v1/holdings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CVA_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An anonymous request is unauthenticated (401)")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/holdings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A company reads only its own holdings")
    void companyReadsOwnHoldings() throws Exception {
        given(registryQueryUseCase.listCompanyHoldings(7L)).willReturn(List.of(new HoldingView(41L, 5L,
                "2026-PRJ11-RPT31-000001_000100", "EV fleet HCMC", 20, 80, 0, "000001..000100")));

        mockMvc.perform(get("/api/v1/holdings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].holdingId").value(41))
                .andExpect(jsonPath("$[0].batchCode").value("2026-PRJ11-RPT31-000001_000100"))
                .andExpect(jsonPath("$[0].availableQty").value(20))
                .andExpect(jsonPath("$[0].lockedQty").value(80));
    }

    @Test
    @DisplayName("Only a company may retire credits")
    void onlyCompanyMayRetire() throws Exception {
        given(registryCommandUseCase.retireCredits(anyLong(), anyLong(), anyLong(), any(), any())).willReturn(77L);

        mockMvc.perform(post("/api/v1/holdings/41/retire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CVA_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20,\"beneficiaryName\":\"VinFast\",\"reason\":\"offset\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/holdings/41/retire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20,\"beneficiaryName\":\"VinFast\",\"reason\":\"offset\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77));

        then(registryCommandUseCase).should().retireCredits(7L, 41L, 20L, "VinFast", "offset");
    }
}
