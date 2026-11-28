import type {
  KycBadgeStatus,
  Money,
  VehicleVerificationStatus,
} from "../../../domain/model/MobileDomainContracts.ts";

/**
 * Inbound CQRS Query Port for CarbonX Mobile App (4G/5G-optimized read contracts).
 * Maps 1-to-1 with Java {@code com.carbonx.mobilebff.application.port.in.MobileBffQueryUseCase}.
 */
export interface MobileEvHomeQueryPort {
  getEvOwnerHomeBootstrap(evOwnerUserId: number): Promise<MobileEvHomeBootstrapView>;

  getCursorPayoutProvenanceFeed(
    evOwnerUserId: number,
    nextCursor: string | null,
    limit: number,
  ): Promise<MobilePayoutFeedSlice>;

  listMyCompactVehicles(evOwnerUserId: number): Promise<readonly MobileVehicleCardView[]>;
}

export interface MobileEvHomeBootstrapView {
  readonly userId: number;
  readonly displayName: string;
  readonly kycBadgeStatus: KycBadgeStatus;
  readonly verifiedVehicleCount: number;
  readonly availableWalletBalance: Money;
  readonly lifetimeKwhContributed: string;
  readonly unreadNotificationsCount: number;
  readonly latestPayouts: readonly CompactPayoutCard[];
  /** True when a non-critical downstream microservice tripped Resilience4j CircuitBreaker. */
  readonly degradedMode: boolean;
}

export interface CompactPayoutCard {
  readonly walletTxId: number;
  readonly creditedAmount: Money;
  readonly batchCode: string;
  readonly serialRange: string;
  readonly kwhContributed: string;
  readonly paidAt: string;
}

export interface MobilePayoutFeedSlice {
  readonly items: readonly CompactPayoutCard[];
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
}

export interface MobileVehicleCardView {
  readonly vehicleId: number;
  readonly licensePlate: string;
  readonly vehicleModel: string;
  readonly verificationStatus: VehicleVerificationStatus;
  readonly linkedCompanyName: string;
}
