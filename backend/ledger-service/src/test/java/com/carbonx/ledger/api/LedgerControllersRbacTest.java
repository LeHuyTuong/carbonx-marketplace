package com.carbonx.ledger.api;

import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.WalletView;
import com.carbonx.ledger.application.security.AuthenticatedUser;
import com.carbonx.ledger.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.ledger.infrastructure.security.JwtTokenVerifier;
import com.carbonx.ledger.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WalletController.class, WithdrawalController.class, InternalWalletController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "carbonx.security.internal-token=test-internal-secret-at-least-32-bytes-long")
class LedgerControllersRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @MockBean
    private LedgerCommandUseCase ledgerCommandUseCase;

    @MockBean
    private LedgerQueryUseCase ledgerQueryUseCase;

    @Test
    void internalWalletEndpointRejectsUserJwtAndAcceptsInternalToken() throws Exception {
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "admin@x.vn", "ROLE_ADMIN")));

        String body = "{\"idempotencyKey\":\"k1\",\"buyerUserId\":2,\"sellerUserId\":3,\"grossAmount\":10,\"platformFee\":0.5}";

        mockMvc.perform(post("/api/v1/internal/wallets/orders/1/settle")
                        .header("Authorization", "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/internal/wallets/orders/1/settle")
                        .header("X-Internal-Service-Token", "test-internal-secret-at-least-32-bytes-long")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void walletAndAdminWithdrawalRbac() throws Exception {
        given(jwtTokenVerifier.verify("user-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(7L, "u@x.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify("admin-jwt"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "a@x.vn", "ROLE_ADMIN")));
        given(ledgerQueryUseCase.getOrCreateWallet(7L))
                .willReturn(new WalletView(1L, 7L, BigDecimal.TEN, "USD", Instant.now()));
        given(ledgerQueryUseCase.listAllWithdrawals()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/wallet")
                        .header("Authorization", "Bearer user-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10));

        mockMvc.perform(get("/api/v1/withdrawal/admin")
                        .header("Authorization", "Bearer user-jwt"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/withdrawal/admin")
                        .header("Authorization", "Bearer admin-jwt"))
                .andExpect(status().isOk());
    }
}
