package com.carbonx.identity.api;

import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.UserAdminResult;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.UserSummaryView;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserManagementController.class, PasswordController.class, KycController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserProvisionRbacTest {

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

    @Test
    @DisplayName("Only ADMIN can list and create CVA/ADMIN platform accounts")
    void adminCanListAndProvisionUsersWhileCompanyIsForbidden() throws Exception {
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, null, "admin@carbonx.vn", "ROLE_ADMIN")));
        given(jwtTokenVerifier.verify("company-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(2L, 10L, "co@carbonx.vn", "ROLE_COMPANY")));

        given(identityQueryUseCase.listAllUsersForAdmin()).willReturn(List.of(
                new UserSummaryView(1L, "admin@carbonx.vn", "Admin", "ROLE_ADMIN", "ACTIVE", Instant.now())));
        given(identityCommandUseCase.provisionUserByAdmin(eq(1L), any()))
                .willReturn(new UserAdminResult(9L, "cva@carbonx.vn", "Lead CVA", "ROLE_CVA", "ACTIVE", Instant.now()));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer company-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cva@carbonx.vn\",\"password\":\"Cva@12345\",\"fullName\":\"Lead CVA\",\"role\":\"CVA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ROLE_CVA"));
    }
}
