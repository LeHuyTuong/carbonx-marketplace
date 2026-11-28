package com.carbonx.identity.api;

import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.VehicleView;
import com.carbonx.identity.application.security.AuthenticatedUser;
import com.carbonx.identity.infrastructure.security.GoogleOAuth2SuccessHandler;
import com.carbonx.identity.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.identity.infrastructure.security.JwtTokenVerifier;
import com.carbonx.identity.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC regression gate: the platform-wide vehicle listing is ADMIN-only and an EV owner must be
 * rejected with 403 by the server, independently of any frontend hiding.
 */
@WebMvcTest(controllers = VehicleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VehicleAccessRbacTest {

    private static final String EV_OWNER_TOKEN = "ev-owner-token";
    private static final String ADMIN_TOKEN = "admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityCommandUseCase identityCommandUseCase;

    @MockBean
    private IdentityQueryUseCase identityQueryUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @MockBean
    private GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

    @BeforeEach
    void stubTokenVerification() {
        given(jwtTokenVerifier.verify(EV_OWNER_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "owner@carbonx.vn", "ROLE_EV_OWNER")));
        given(jwtTokenVerifier.verify(ADMIN_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@carbonx.vn", "ROLE_ADMIN")));
        given(jwtTokenVerifier.verify("company-token"))
                .willReturn(Optional.of(new AuthenticatedUser(8L, "company@carbonx.vn", "ROLE_COMPANY")));
    }

    @Test
    @DisplayName("EV_OWNER calling GET /api/v1/vehicles/list is rejected with 403 and never reaches the query port")
    void evOwnerCannotListAllVehicles() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/list")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN))
                .andExpect(status().isForbidden());

        then(identityQueryUseCase).should(never()).listAllVehiclesForAdmin(0, 20);
    }

    @Test
    @DisplayName("ROLE_COMPANY calling GET /api/v1/vehicles/list is rejected with 403")
    void companyCannotListAllVehicles() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/list")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer company-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN calling GET /api/v1/vehicles/list receives the vehicle projection")
    void adminCanListAllVehicles() throws Exception {
        given(identityQueryUseCase.listAllVehiclesForAdmin(0, 20))
                .willReturn(List.of(new VehicleView(99L, 7L, 3L, "51A12345", "VinFast", "VF8", "ACTIVE")));

        mockMvc.perform(get("/api/v1/vehicles/list")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").doesNotExist())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].plateNumber").value("51A12345"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("A request without a bearer token is unauthenticated (401)")
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An invalid bearer token is treated as anonymous (401)")
    void invalidTokenIsUnauthorized() throws Exception {
        given(jwtTokenVerifier.verify("forged-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vehicles/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("EV_OWNER can still read their own vehicles")
    void evOwnerCanListOwnVehicles() throws Exception {
        given(identityQueryUseCase.listMyVehicles(7L))
                .willReturn(List.of(new VehicleView(99L, 7L, 3L, "51A12345", "VinFast", "VF8", "ACTIVE")));

        mockMvc.perform(get("/api/v1/vehicles/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plateNumber").value("51A12345"));
    }
}
