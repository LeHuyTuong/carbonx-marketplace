package com.carbonx.identity.api.dto;

import com.carbonx.identity.application.port.in.IdentityQueryUseCase.CompanyView;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.EvKycView;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.UserSummaryView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** KYC submission, profile lookup and review contracts. */
public final class KycDtos {

    private KycDtos() {
    }

    public record EvOwnerKycRequest(
            @NotBlank @Size(max = 32) String documentType,
            @NotBlank @Size(max = 64) String documentNumber,
            @Size(max = 32) String phone) {
    }

    public record EvOwnerFullKycRequest(
            @Size(max = 191) String name,
            @Size(max = 32) String phone,
            @Size(max = 128) String country,
            @Size(max = 500) String address,
            @Size(max = 32) String birthDate,
            @NotBlank @Size(max = 32) String documentType,
            @NotBlank @Size(max = 64) String documentNumber,
            @Size(max = 16) String gender) {
    }

    public record CompanyKycPayload(
            @Size(max = 255) String businessName,
            @Size(max = 255) String companyName,
            @Size(max = 64) String taxCode,
            @Size(max = 128) String businessLicense,
            @Size(max = 500) String address) {
    }

    public record CompanyKycRequest(
            @Size(max = 255) String businessName,
            @Size(max = 255) String companyName,
            @Size(max = 64) String taxCode,
            @Size(max = 128) String businessLicense,
            @Size(max = 500) String address,
            CompanyKycPayload data) {

        public String resolvedBusinessName() {
            CompanyKycPayload source = data != null ? data : null;
            String candidate = firstNonBlank(businessName, companyName,
                    source == null ? null : source.businessName(),
                    source == null ? null : source.companyName());
            return candidate;
        }

        public String resolvedTaxCode() {
            return firstNonBlank(taxCode, data == null ? null : data.taxCode());
        }

        public String resolvedBusinessLicense() {
            return firstNonBlank(businessLicense, data == null ? null : data.businessLicense());
        }

        public String resolvedAddress() {
            return firstNonBlank(address, data == null ? null : data.address());
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
        }
    }

    public record CompanyReviewRequest(
            @NotNull Boolean approved,
            @Size(max = 500) String note) {
    }

    public record CompanyKycResponse(
            Long id,
            Long userId,
            String businessName,
            String companyName,
            String taxCode,
            String businessLicense,
            String address,
            String kycStatus,
            String status,
            Instant createAt) {

        public static CompanyKycResponse from(CompanyView view) {
            return new CompanyKycResponse(view.companyId(), view.userId(), view.businessName(),
                    view.businessName(), view.taxCode(), view.businessLicense(), view.address(),
                    view.kycStatus(), view.kycStatus(), view.createdAt());
        }
    }

    public record EvOwnerKycResponse(
            Long id,
            Long userId,
            String name,
            String email,
            String phone,
            String country,
            String address,
            String birthDate,
            String documentType,
            String documentNumber,
            String gender,
            String status,
            Instant createdAt) {

        public static EvOwnerKycResponse from(EvKycView view) {
            return new EvOwnerKycResponse(view.id(), view.evOwnerId(), view.name(), view.email(),
                    view.phone(), view.country(), view.address(), view.birthDate(), view.documentType(),
                    view.documentNumber(), view.gender(), view.status(), view.createdAt());
        }
    }

    public record StaffKycProfileResponse(
            Long id,
            String name,
            String fullName,
            String email,
            String role,
            String organization,
            String positionTitle,
            String accreditationNo,
            String status,
            Instant createdAt) {

        public static StaffKycProfileResponse from(UserSummaryView user) {
            String displayName = user.fullName() != null && !user.fullName().isBlank()
                    ? user.fullName()
                    : user.email();
            boolean isAdmin = "ROLE_ADMIN".equals(user.role());
            String org = isAdmin ? "CarbonX Platform Governance" : "Independent Carbon Verification Body";
            String title = isAdmin ? "System Administrator" : "Lead Carbon Auditor";
            String cert = isAdmin ? "CARB-ADMIN-OPS" : "ISO-14065-VN";
            return new StaffKycProfileResponse(user.id(), displayName, displayName, user.email(),
                    user.role(), org, title, cert, user.status(), user.createdAt());
        }
    }
}
