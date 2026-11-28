package com.carbonx.registry.api;

import com.carbonx.common.security.InternalServiceAuth;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase.TransferResult;
import com.carbonx.registry.application.security.AuthenticatedUser;
import com.carbonx.registry.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.registry.infrastructure.security.JwtTokenVerifier;
import com.carbonx.registry.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalRegistryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "carbonx.security.internal-token=carbonx-internal-test-only-token-32b")
class InternalRegistrySecurityTest {

    private static final String INTERNAL_TOKEN = "carbonx-internal-test-only-token-32b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistryCommandUseCase registryCommandUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @Test
    @DisplayName("A COMPANY user JWT cannot directly invoke /api/v1/internal/holdings/transfer to bypass payment")
    void endUserCompanyJwtRejectedOnInternalTransfer() throws Exception {
        given(jwtTokenVerifier.verify("attacker-company-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(99L, 12L, "attacker@carbonx.vn", "ROLE_COMPANY")));

        mockMvc.perform(post("/api/v1/internal/holdings/5/transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer attacker-company-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"k1\",\"buyerCompanyId\":12,\"quantity\":50,\"orderRef\":\"O1\"}"))
                .andExpect(status().isForbidden());

        then(registryCommandUseCase).should(never())
                .transferLockedCredits("k1", 5L, 12L, 50L, "O1");
    }

    @Test
    @DisplayName("Trading-service with valid X-Internal-Service-Token can execute transfer")
    void internalTokenExecutesTransfer() throws Exception {
        given(registryCommandUseCase.transferLockedCredits("k1", 5L, 12L, 50L, "O1"))
                .willReturn(new TransferResult(88L, 1L, 1L, 50L));

        mockMvc.perform(post("/api/v1/internal/holdings/5/transfer")
                        .header(InternalServiceAuth.HEADER_NAME, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"k1\",\"buyerCompanyId\":12,\"quantity\":50,\"orderRef\":\"O1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerHoldingId").value(88))
                .andExpect(jsonPath("$.serialStart").value(1))
                .andExpect(jsonPath("$.serialEnd").value(50));
    }
}
