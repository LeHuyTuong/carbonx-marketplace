package com.carbonx.mobilebff.application.port.in;

import com.carbonx.common.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Inbound CQRS Query Port tailored for CarbonX Mobile App (EV Owner on 4G/5G networks).
 * Collapses multiple chatty REST calls into 1 compact home payload and uses cursor pagination for infinite scroll.
 */
public interface MobileBffQueryUseCase {

    MobileEvHomeBootstrapView getEvOwnerHomeBootstrap(Long evOwnerUserId);

    MobilePayoutFeedSlice getCursorPayoutProvenanceFeed(Long evOwnerUserId, String nextCursor, int limit);

    List<MobileVehicleCardView> listMyCompactVehicles(Long evOwnerUserId);

    record MobileEvHomeBootstrapView(
            Long userId,
            String displayName,
            String kycBadgeStatus,
            int verifiedVehicleCount,
            Money availableWalletBalance,
            BigDecimal lifetimeKwhContributed,
            int unreadNotificationsCount,
            List<CompactPayoutCard> latestPayouts,
            boolean degradedMode
    ) {}

    record CompactPayoutCard(
            Long walletTxId,
            Money creditedAmount,
            String batchCode,
            String serialRange,
            BigDecimal kwhContributed,
            Instant paidAt
    ) {}

    record MobilePayoutFeedSlice(
            List<CompactPayoutCard> items,
            String nextCursor,
            boolean hasMore
    ) {}

    record MobileVehicleCardView(
            Long vehicleId,
            String licensePlate,
            String vehicleModel,
            String verificationStatus,
            String linkedCompanyName
    ) {}
}
