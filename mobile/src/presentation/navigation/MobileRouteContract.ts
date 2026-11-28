import type {
  CompactPayoutCard,
  MobileEvHomeBootstrapView,
  MobileVehicleCardView,
} from "../../application/port/in/MobileEvHomeQueryPort.ts";
import type { PresignedCameraUploadGrant } from "../../application/port/in/MobileEvActionsCommandPort.ts";
import type { UploadDocumentType } from "../../domain/model/MobileDomainContracts.ts";

/**
 * Typed Expo Router Navigation Contract & Presentation ViewModel Contracts.
 * Every screen strictly consumes CQRS Query/Command Ports (zero direct domain business rules in UI).
 */

export type MobileAppRoute =
  | { readonly name: "/(tabs)/home" }
  | { readonly name: "/(tabs)/vehicles" }
  | { readonly name: "/(tabs)/payouts" }
  | { readonly name: "/(tabs)/wallet" }
  | { readonly name: "/kyc-camera"; readonly params: { readonly documentType: UploadDocumentType } }
  | { readonly name: "/payout-provenance/[txId]"; readonly params: { readonly txId: number } }
  | { readonly name: "/withdraw-confirm" };

export interface HomeTabScreenContract {
  readonly state: MobileEvHomeBootstrapView;
  readonly onRefresh: () => Promise<void>;
  readonly onOpenPayoutProvenance: (walletTxId: number) => void;
  readonly onOpenWithdrawSheet: () => void;
}

export interface VehiclesTabScreenContract {
  readonly vehicles: readonly MobileVehicleCardView[];
  readonly onStartCameraRegistration: () => void;
}

export interface PayoutsTabScreenContract {
  readonly payouts: readonly CompactPayoutCard[];
  readonly hasMore: boolean;
  readonly onLoadNextCursorPage: () => Promise<void>;
  readonly onInspectBatchProvenance: (card: CompactPayoutCard) => void;
}

export interface CameraKycUploadScreenContract {
  readonly documentType: UploadDocumentType;
  readonly activeGrant: PresignedCameraUploadGrant | null;
  readonly onCaptureAndUploadToS3: () => Promise<void>;
}
