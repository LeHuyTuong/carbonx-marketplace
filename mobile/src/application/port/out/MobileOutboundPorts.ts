import type { IdempotencyKey, PushPlatform } from "../../../domain/model/MobileDomainContracts.ts";

/**
 * Outbound Hexagonal Infrastructure Ports implemented in src/infrastructure/ by Expo/Native adapters.
 */

export interface MobileBffHttpGatewayPort {
  get<TResponse>(path: string, queryParams?: Record<string, string | number | null>): Promise<TResponse>;
  post<TRequest, TResponse>(
    path: string,
    body: TRequest,
    options?: { readonly idempotencyKey?: IdempotencyKey; readonly traceId?: string },
  ): Promise<TResponse>;
  delete<TResponse>(path: string, body?: unknown): Promise<TResponse>;
}

/** MUST store tokens in iOS Keychain / Android Keystore via expo-secure-store (never AsyncStorage plaintext). */
export interface SecureTokenVaultPort {
  saveTokens(accessToken: string, refreshToken: string): Promise<void>;
  readAccessToken(): Promise<string | null>;
  clearTokens(): Promise<void>;
}

export interface CapturedPhotoAsset {
  readonly localUri: string;
  readonly mimeType: "image/jpeg" | "image/png" | "image/heic";
  readonly byteSize: number;
}

export interface CameraMediaPort {
  captureDocumentPhoto(): Promise<CapturedPhotoAsset | null>;
  uploadDirectToS3(presignedPutUrl: string, asset: CapturedPhotoAsset): Promise<void>;
}

export interface PushDeepLinkEvent {
  readonly eventType: "PROFIT_DISTRIBUTED" | "WITHDRAWAL_APPROVED" | "KYC_REVIEWED";
  readonly targetRoute: string;
  readonly resourceId: string;
}

export interface PushNotificationPort {
  requestPermissionAndGetToken(): Promise<{ readonly platform: PushPlatform; readonly token: string } | null>;
  subscribeDeepLinkOpens(handler: (event: PushDeepLinkEvent) => void): () => void;
}

export interface BiometricAuthPort {
  confirmSensitiveAction(promptTitle: string): Promise<boolean>;
}
