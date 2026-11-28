package com.carbonx.mrv.api;

import com.carbonx.mrv.application.port.in.MrvCommandUseCase;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.ProjectView;
import com.carbonx.mrv.application.security.AuthenticatedUser;
import com.carbonx.mrv.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.mrv.infrastructure.security.JwtTokenVerifier;
import com.carbonx.mrv.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProjectController.class, ApplicationController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MrvControllersRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @MockBean
    private MrvCommandUseCase mrvCommandUseCase;

    @MockBean
    private MrvQueryUseCase mrvQueryUseCase;

    @Test
    @DisplayName("GET /api/v1/projects/all is public without bearer token")
    void projectsAllIsPublic() throws Exception {
        given(mrvQueryUseCase.listProjects()).willReturn(List.of(
                new ProjectView(1L, "EV Fleet HCMC", "Desc", "OPEN",
                        new BigDecimal("0.676600"), new BigDecimal("0.0500"),
                        new BigDecimal("0.0300"), new BigDecimal("0.0200"), Instant.now())));

        mockMvc.perform(get("/api/v1/projects/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("EV Fleet HCMC"));
    }

    @Test
    @DisplayName("DELETE /api/v1/projects/{id} forbids COMPANY and allows ADMIN")
    void deleteProjectEnforcesAdmin() throws Exception {
        given(jwtTokenVerifier.verify("company-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "co@x.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@x.vn", "ROLE_ADMIN")));

        mockMvc.perform(delete("/api/v1/projects/1")
                        .header("Authorization", "Bearer company-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/projects/1")
                        .header("Authorization", "Bearer admin-jwt"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/project-applications forbids COMPANY and allows ADMIN")
    void listAllApplicationsEnforcesAdmin() throws Exception {
        given(jwtTokenVerifier.verify("company-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "co@x.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@x.vn", "ROLE_ADMIN")));
        given(mrvQueryUseCase.listAllApplications()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/project-applications")
                        .header("Authorization", "Bearer company-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/project-applications")
                        .header("Authorization", "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(contentTypeJson());
    }

    @Test
    @DisplayName("PUT /api/v1/project-applications/{id}/admin-decision requires ADMIN")
    void adminDecisionRequiresAdmin() throws Exception {
        given(jwtTokenVerifier.verify("cva-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(2L, "cva@x.vn", "ROLE_CVA")));
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@x.vn", "ROLE_ADMIN")));

        mockMvc.perform(put("/api/v1/project-applications/10/admin-decision")
                        .param("approved", "true")
                        .header("Authorization", "Bearer cva-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/project-applications/10/admin-decision")
                        .param("approved", "true")
                        .param("note", "ok")
                        .header("Authorization", "Bearer admin-jwt"))
                .andExpect(status().isNoContent());
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentTypeJson() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }
}
