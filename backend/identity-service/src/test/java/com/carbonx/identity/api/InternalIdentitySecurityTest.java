package com.carbonx.identity.api;

import com.carbonx.common.security.InternalServiceAuth;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.CompanyView;
import com.carbonx.identity.application.security.AuthenticatedUser;
import com.carbonx.identity.infrastructure.security.GoogleOAuth2SuccessHandler;
import com.carbonx.identity.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.identity.infrastructure.security.JwtTokenVerifier;
import com.carbonx.identity.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalIdentityController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "carbonx.security.internal-token=carbonx-internal-test-only-token-32b")
class InternalIdentitySecurityTest {

    private static final String INTERNAL_TOKEN = "carbonx-internal-test-only-token-32b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityQueryUseCase identityQueryUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @MockBean
    private GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

    @Test
    @DisplayName("Anonymous callers cannot read internal company directory (401)")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/internal/companies/3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("End-user ROLE_COMPANY JWT without X-Internal-Service-Token is rejected (403)")
    void endUserTokenRejectedOnInternalEndpoint() throws Exception {
        given(jwtTokenVerifier.verify("user-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(8L, 3L, "company@carbonx.vn", "ROLE_COMPANY")));

        mockMvc.perform(get("/api/v1/internal/companies/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Valid X-Internal-Service-Token can resolve canonical company by userId")
    void internalTokenResolvesCompanyByUser() throws Exception {
        given(identityQueryUseCase.getCompanyByUserId(8L))
                .willReturn(new CompanyView(3L, 8L, "Green Fleet JSC", "0312345678", "APPROVED"));

        mockMvc.perform(get("/api/v1/internal/companies/by-user/8")
                        .header(InternalServiceAuth.HEADER_NAME, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(3))
                .andExpect(jsonPath("$.userId").value(8))
                .andExpect(jsonPath("$.kycStatus").value("APPROVED"));
    }
}
