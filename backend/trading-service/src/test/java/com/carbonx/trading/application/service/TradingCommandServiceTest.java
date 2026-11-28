package com.carbonx.trading.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.exception.TradingErrorCode;
import com.carbonx.trading.application.port.out.CreditHoldingProjectionPort;
import com.carbonx.trading.application.port.out.CreditHoldingProjectionPort.HoldingSnapshot;
import com.carbonx.trading.application.port.out.ListingReservationRepositoryPort;
import com.carbonx.trading.application.port.out.MarketListingRepositoryPort;
import com.carbonx.trading.application.port.out.OrderCreditLockPort;
import com.carbonx.trading.application.port.out.PlatformSettingRepositoryPort;
import com.carbonx.trading.domain.order.ListingReservation;
import com.carbonx.trading.domain.order.ListingStatus;
import com.carbonx.trading.domain.order.MarketListing;
import com.carbonx.trading.domain.order.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingCommandServiceTest {

    @Mock
    private MarketListingRepositoryPort listingRepository;
    @Mock
    private ListingReservationRepositoryPort reservationRepository;
    @Mock
    private CreditHoldingProjectionPort holdingProjection;
    @Mock
    private OrderCreditLockPort creditLock;
    @Mock
    private OrderSagaService orderSagaService;
    @Mock
    private PlatformSettingRepositoryPort platformSettingRepository;

    private TradingCommandService service;

    @BeforeEach
    void setUp() {
        service = new TradingCommandService(
                listingRepository, reservationRepository, holdingProjection, creditLock,
                orderSagaService, platformSettingRepository);
    }

    @Test
    void createListingLocksCreditsInRegistryAndPersistsSellerUserId() {
        when(holdingProjection.fetchOwnedHolding(10L))
                .thenReturn(new HoldingSnapshot(10L, 100L, "2026-B1", "EV Charge Fleet", 80L));
        when(listingRepository.save(any())).thenAnswer(inv -> ((MarketListing) inv.getArgument(0)).withId(55L));

        Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
        Long listingId = service.createListing(42L, 7L, 10L, 30L, Money.of(new BigDecimal("15.50")), expires);

        assertThat(listingId).isEqualTo(55L);
        verify(creditLock).lockCreditsForListing(
                argThat(k -> k.startsWith("listing-lock:")), eq(10L), eq(42L), eq(30L), eq("holding-10"));

        ArgumentCaptor<MarketListing> savedCaptor = ArgumentCaptor.forClass(MarketListing.class);
        verify(listingRepository).save(savedCaptor.capture());
        MarketListing saved = savedCaptor.getValue();
        assertThat(saved.sellerCompanyId()).isEqualTo(42L);
        assertThat(saved.sellerUserId()).isEqualTo(7L);
        assertThat(saved.availableQty()).isEqualTo(30L);
    }

    @Test
    void createListingRollsBackRegistryLockIfListingPersistenceFails() {
        when(holdingProjection.fetchOwnedHolding(10L))
                .thenReturn(new HoldingSnapshot(10L, 100L, "2026-B1", "EV Charge Fleet", 80L));
        when(listingRepository.save(any())).thenThrow(new IllegalStateException("DB write failure"));

        assertThatThrownBy(() -> service.createListing(42L, 7L, 10L, 25L, Money.of(new BigDecimal("12.00")), null))
                .isInstanceOf(IllegalStateException.class);

        verify(creditLock).lockCreditsForListing(any(), eq(10L), eq(42L), eq(25L), any());
        verify(creditLock).releaseLockedCredits(
                argThat(k -> k.endsWith(":rollback")), eq(10L), eq(25L), any());
    }

    @Test
    void cancelListingReleasesUnsoldCreditsAndRejectsReservedOrForeignListing() {
        MarketListing active = new MarketListing(
                5L, 10L, 100L, "2026-B1", "EV Fleet", 42L, 7L, 50L, 30L, Money.of(new BigDecimal("10.00")),
                ListingStatus.LISTED, null, 0L);
        when(listingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(active));
        when(reservationRepository.findByListingIdAndStatus(5L, ReservationStatus.RESERVED)).thenReturn(List.of());

        service.cancelListing(42L, 5L);

        verify(listingRepository).save(argThat(l -> l.status() == ListingStatus.CANCELLED));
        verify(creditLock).releaseLockedCredits("listing-cancel:5", 10L, 30L, "Listing 5 cancelled");
    }

    @Test
    void cancelListingWithActiveReservationsReturnsConflictAndLeavesCreditsLocked() {
        MarketListing active = MarketListing.list(10L, 100L, "2026-B1", "EV Fleet", 42L, 7L, 50L,
                Money.of(new BigDecimal("10.00")), null).withId(5L);
        when(listingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(active));
        when(reservationRepository.findByListingIdAndStatus(5L, ReservationStatus.RESERVED))
                .thenReturn(List.of(ListingReservation.reserve(5L, 99L, "idem-1", 10L, Instant.now().plusSeconds(60))));

        assertThatThrownBy(() -> service.cancelListing(42L, 5L))
                .isInstanceOf(TradingApplicationException.class)
                .extracting(ex -> ((TradingApplicationException) ex).code())
                .isEqualTo(TradingErrorCode.CONFLICT);

        verify(creditLock, never()).releaseLockedCredits(any(), any(), eq(50L), any());
    }
}
