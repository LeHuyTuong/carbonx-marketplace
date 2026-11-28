import type { MobileEvActionsCommandPort } from "../../application/port/in/MobileEvActionsCommandPort.ts";
import type { MobileEvHomeQueryPort } from "../../application/port/in/MobileEvHomeQueryPort.ts";
import type {
  BiometricAuthPort,
  CameraMediaPort,
  MobileBffHttpGatewayPort,
  PushNotificationPort,
  SecureTokenVaultPort,
} from "../../application/port/out/MobileOutboundPorts.ts";

/**
 * Pure Composition Root / Dependency Injection Contract for CarbonX Mobile.
 * Binds /api/mobile/** Gateway, SecureStore Vault, Camera S3 Uploader, Push & Biometric adapters
 * to the CQRS Query & Command use-case ports without coupling domain/application to Expo SDKs.
 */
export interface MobileAppDependencies {
  readonly bffBaseUrl: string;
  readonly httpGateway: MobileBffHttpGatewayPort;
  readonly tokenVault: SecureTokenVaultPort;
  readonly cameraMedia: CameraMediaPort;
  readonly pushNotifications: PushNotificationPort;
  readonly biometricAuth: BiometricAuthPort;
  readonly queryUseCases: MobileEvHomeQueryPort;
  readonly commandUseCases: MobileEvActionsCommandPort;
}
