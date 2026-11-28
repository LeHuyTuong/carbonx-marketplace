package com.carbonx.trading.api;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.api.error.GlobalExceptionHandler;
import com.carbonx.trading.application.port.in.TradingCommandUseCase;
import com.carbonx.trading.application.port.in.TradingQueryUseCase;
import com.carbonx.trading.application.port.in.TradingQueryUseCase.ListingView;
import com.carbonx.trading.application.security.AuthenticatedUser;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ListingController.class, OrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class OrderAccessRbacTest {

    private static final String COMPANY_TOKEN = "company-token";
    private static final String EV_OWNER_TOKEN = "ev-owner-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingCommandUseCase tradingCommandUseCase;

    @MockBean
    private TradingQueryUseCase tradingQueryUseCase;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @BeforeEach
    void setUpTokens() {
        given(jwtTokenVerifier.verify(COMPANY_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(7L, 42L, "company@carbonx.vn", "ROLE_COMPANY")));
        given(jwtTokenVerifier.verify(EV_OWNER_TOKEN))
                .willReturn(Optional.of(new AuthenticatedUser(9L, null, "owner@carbonx.vn", "ROLE_EV_OWNER")));
    }

    @Test
    void anonymousUserCanReadListingsAndFeeSettingButGets401OnProtectedRoutes() throws Exception {
        given(tradingQueryUseCase.listActiveMarketplace(null, null)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/trading/listings"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/trading/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/trading/orders")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":5,\"quantity\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void evOwnerCannotCreateListingOrPlaceOrder() throws Exception {
        mockMvc.perform(post("/api/v1/trading/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdingId\":10,\"quantity\":5,\"pricePerCredit\":15.0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN)
                        .header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":5,\"quantity\":10}"))
                .andExpect(status().isForbidden());

        then(tradingCommandUseCase).shouldHaveNoInteractions();
    }

    @Test
    void authenticatedEvOwnerCanBrowseMarketplaceListings() throws Exception {
        given(tradingQueryUseCase.listActiveMarketplace(null, null))
                .willReturn(List.of(new ListingView(5L, 100L, "B-1", "EV Fleet", 42L, 30L,
                        Money.of(new BigDecimal("15.00")), "LISTED")));

        mockMvc.perform(get("/api/v1/trading/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EV_OWNER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    void companyPassesCanonicalCompanyIdAndUserIdToCommandsAndQueries() throws Exception {
        given(tradingCommandUseCase.createListing(eq(42L), eq(7L), eq(10L), eq(25L), any(), any()))
                .willReturn(55L);
        given(tradingCommandUseCase.executeInstantBuyOrderSaga("buy-1", 42L, 7L, 5L, 10L))
                .willReturn(500L);
        given(tradingQueryUseCase.listMyOrders(42L)).willReturn(List.of());

        mockMvc.perform(post("/api/v1/trading/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdingId\":10,\"quantity\":25,\"pricePerCredit\":15.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55));

        mockMvc.perform(post("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN)
                        .header("Idempotency-Key", "buy-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":5,\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/trading/listings/55")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COMPANY_TOKEN))
                .andExpect(status().isNoContent());

        then(tradingCommandUseCase).should().cancelListing(42L, 55L);
        then(tradingQueryUseCase).should(never()).listMyOrders(7L);
    }
}
