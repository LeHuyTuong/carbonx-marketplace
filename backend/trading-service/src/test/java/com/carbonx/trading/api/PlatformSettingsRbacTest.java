package com.carbonx.trading.api;

import com.carbonx.trading.application.port.in.TradingCommandUseCase;
import com.carbonx.trading.application.port.in.TradingQueryUseCase;
import com.carbonx.trading.application.security.AuthenticatedUser;
import com.carbonx.trading.domain.setting.TradingFeeSetting;
import com.carbonx.trading.infrastructure.security.JwtAuthenticationFilter;
import com.carbonx.trading.infrastructure.security.JwtTokenVerifier;
import com.carbonx.trading.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformSettingsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PlatformSettingsRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingCommandUseCase tradingCommandUseCase;

    @MockBean
    private TradingQueryUseCase tradingQueryUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @BeforeEach
    void setUp() {
        given(jwtTokenVerifier.verify("admin-token"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, null, "admin@carbonx.vn", "ROLE_ADMIN")));
        given(jwtTokenVerifier.verify("company-token"))
                .willReturn(Optional.of(new AuthenticatedUser(7L, 42L, "company@carbonx.vn", "ROLE_COMPANY")));
        given(tradingQueryUseCase.getTradingFeeSetting())
                .willReturn(TradingFeeSetting.ofRate(new BigDecimal("0.0500"), 1L, Instant.now()));
        given(tradingCommandUseCase.updateTradingFeeRate(eq(1L), eq(new BigDecimal("0.0350"))))
                .willReturn(TradingFeeSetting.ofRate(new BigDecimal("0.0350"), 1L, Instant.now()));
    }

    @Test
    void guestCanReadFeeSettingWhileOnlyAdminCanUpdateIt() throws Exception {
        mockMvc.perform(get("/api/v1/trading/settings/fee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeRate").value(0.05))
                .andExpect(jsonPath("$.feePercent").value(5.0));

        mockMvc.perform(put("/api/v1/trading/settings/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feePercent\":3.5}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/trading/settings/fee")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer company-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feePercent\":3.5}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/trading/settings/fee")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feePercent\":3.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeRate").value(0.035))
                .andExpect(jsonPath("$.feePercent").value(3.5));

        then(tradingCommandUseCase).should().updateTradingFeeRate(1L, new BigDecimal("0.0350"));
    }
}
