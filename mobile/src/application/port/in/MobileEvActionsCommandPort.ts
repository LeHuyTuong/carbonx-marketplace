import type {
  IdempotencyKey,
  Money,
  PushPlatform,
  UploadDocumentType,
} from "../../../domain/model/MobileDomainContracts.ts";

/**
 * Inbound CQRS Command Port for CarbonX Mobile App.
 * Maps 1-to-1 with Java {@code com.carbonx.mobilebff.application.port.in.MobileBffCommandUseCase}.
 */
export interface MobileEvActionsCommandPort {
  requestCameraUploadUrl(command: RequestCameraUploadCommand): Promise<PresignedCameraUploadGrant>;

  submitMobileVehicleRegistration(command: SubmitMobileVehicleCommand): Promise<number>;

  registerMobilePushDevice(command: RegisterPushDeviceCommand): Promise<void>;

  revokeMobilePushDevice(userId: number, deviceToken: string): Promise<void>;

  requestMobileWithdrawal(command: RequestMobileWithdrawalCommand): Promise<number>;
}

export interface RequestCameraUploadCommand {
  readonly evOwnerUserId: number;
  readonly documentType: UploadDocumentType;
  readonly contentType: "image/jpeg" | "image/png" | "image/heic";
  readonly contentLengthBytes: number;
}

export interface PresignedCameraUploadGrant {
  readonly uploadPutUrl: string;
  readonly s3ObjectKey: string;
  readonly expiresAt: string;
  readonly maxAllowedBytes: number;
}

export interface SubmitMobileVehicleCommand {
  readonly idempotencyKey: IdempotencyKey;
  readonly evOwnerUserId: number;
  readonly licensePlate: string;
  readonly vin: string;
  readonly vehicleModel: string;
  readonly s3ObjectKey: string;
}

export interface RegisterPushDeviceCommand {
  readonly userId: number;
  readonly platform: PushPlatform;
  readonly deviceToken: string;
  readonly appVersion: string;
}

export interface RequestMobileWithdrawalCommand {
  readonly idempotencyKey: IdempotencyKey;
  readonly evOwnerUserId: number;
  readonly amount: Money;
  readonly bankCode: string;
  readonly bankAccountNumber: string;
}
