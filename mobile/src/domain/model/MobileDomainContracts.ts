/**
 * Pure Domain Layer for CarbonX Mobile (EV Owner bounded view contracts & value objects).
 * MUST remain 100% independent of Expo, React Native, HTTP clients, and storage SDKs.
 */

export type CurrencyCode = "USD" | "VND";

export interface Money {
  readonly amount: string;
  readonly currency: CurrencyCode;
}

export type IdempotencyKey = string & { readonly __brand: "IdempotencyKey" };

export type KycBadgeStatus = "PENDING" | "UNDER_REVIEW" | "APPROVED" | "REJECTED";

export type VehicleVerificationStatus = "PENDING" | "ACTIVE" | "REJECTED" | "SUSPENDED";

export type UploadDocumentType =
  | "KYC_ID_CARD_FRONT"
  | "KYC_ID_CARD_BACK"
  | "VEHICLE_REGISTRATION_CERTIFICATE";

export type PushPlatform = "FCM_ANDROID" | "APNS_IOS";

export type MobileDomainError =
  | { readonly code: "UNAUTHENTICATED"; readonly message: string }
  | { readonly code: "FORBIDDEN_ROLE"; readonly requiredRole: "EV_OWNER" }
  | { readonly code: "BIOMETRIC_CANCELLED"; readonly message: string }
  | { readonly code: "DOWNSTREAM_DEGRADED"; readonly retryAfterSeconds: number; readonly partialDataServed: boolean }
  | { readonly code: "FILE_TOO_LARGE"; readonly maxAllowedBytes: number }
  | { readonly code: "INSUFFICIENT_WALLET_BALANCE"; readonly available: Money }
  | { readonly code: "DUPLICATE_IDEMPOTENCY_KEY"; readonly existingRefId: number }
  | { readonly code: "VALIDATION_ERROR"; readonly field: string; readonly reason: string };
