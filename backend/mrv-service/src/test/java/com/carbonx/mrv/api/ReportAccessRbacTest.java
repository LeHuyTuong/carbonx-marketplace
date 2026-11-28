package com.carbonx.mrv.api;

import com.carbonx.mrv.application.port.in.MrvCommandUseCase;
import com.carbonx.mrv.application.port.in.MrvCommandUseCase.AiEvaluationView;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.EmissionReportView;
import com.carbonx.mrv.application.security.AuthenticatedUser;
import com.carbonx.mrv.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.mrv.infrastructure.security.JwtTokenVerifier;
import com.carbonx.mrv.infrastructure.security.SecurityConfig;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC regression gate: the CVA queue is CVA-only, companies may only read their own reports and
 * the admin decision is ADMIN-only — enforced by the server independently of any frontend.
 */
@WebMvcTest(controllers = ReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReportAccessRbacTest {

    private static final String COMPANY_TOKEN = "company-token";
    private static final String CVA_TOKEN = "cva-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String EV_OWNER_TOKEN = "ev-owner-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MrvCommandUseCase mrvCommandUseCase;

    @MockBean
    private MrvQueryUseCase mrvQueryUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @BeforeEach
    void stubTokens() {
        given(jwtTokenVerifier.verify(COMPANY_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "company@carbonx.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify(CVA_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(2L, "cva@carbonx.vn", "ROLE_CVA")));
        given(jwtTokenVerifier.verify(ADMIN_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@carbonx.vn", "ROLE_ADMIN")));
        given(jwtTokenVerifier.verify(EV_OWNER_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(9L, "owner@carbonx.vn", "ROLE_EV_OWNER")));
    }

    @Test
    @DisplayName("EV_OWNER calling GET /api/v1/reports/pending-cva is rejected with 403")
    void evOwnerCannotReadCvaQueue() throws Exception {
        mockMvc.perform(get("/api/v1/reports/pending-cva")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN))
                .andExpect(status().isForbidden());

        then(mrvQueryUseCase).should(never()).listPendingReportsForCva();
    }

    @Test
    @DisplayName("COMPANY calling GET /api/v1/reports/pending-cva is rejected with 403")
    void companyCannotReadCvaQueue() throws Exception {
        mockMvc.perform(get("/api/v1/reports/pending-cva")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CVA calling GET /api/v1/reports/my is rejected with 403")
    void cvaCannotReadCompanyReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CVA_TOKEN))
                .andExpect(status().isForbidden());

        then(mrvQueryUseCase).should(never()).listMyReports(2L);
    }

    @Test
    @DisplayName("COMPANY sees only its own reports")
    void companyReadsOwnReports() throws Exception {
        given(mrvQueryUseCase.listMyReports(7L)).willReturn(List.of(new EmissionReportView(31L, 11L,
                "EV fleet HCMC", 7L, "2026-09", new BigDecimal("300.0000"), new BigDecimal("202.9800"),
                3, "SUBMITTED", null, null, null)));

        mockMvc.perform(get("/api/v1/reports/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(31))
                .andExpect(jsonPath("$[0].projectTitle").value("EV fleet HCMC"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("An anonymous request is unauthenticated (401)")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reports/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A COMPANY cannot ask for an AI pre-score (CVA-only)")
    void companyCannotRequestAiScore() throws Exception {
        mockMvc.perform(post("/api/v1/reports/31/ai-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isForbidden());

        then(mrvCommandUseCase).should(never()).suggestAiScoreForCva(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("The CVA receives the AI pre-score for a report")
    void cvaReceivesAiScore() throws Exception {
        given(mrvCommandUseCase.suggestAiScoreForCva(2L, 31L))
                .willReturn(new AiEvaluationView(91.25, "spring-ai-openai", "Dữ liệu tốt"));

        mockMvc.perform(post("/api/v1/reports/31/ai-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CVA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPreScore").value(91.25))
                .andExpect(jsonPath("$.aiVersion").value("spring-ai-openai"));
    }

    @Test
    @DisplayName("Only ADMIN may take the final decision on a report")
    void onlyAdminCanApprove() throws Exception {
        mockMvc.perform(post("/api/v1/reports/31/admin-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CVA_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"note\":\"ok\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reports/31/admin-approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"note\":\"ok\"}"))
                .andExpect(status().isNoContent());

        then(mrvCommandUseCase).should().approveReportByAdmin(1L, 31L, true, "ok");
    }
}
