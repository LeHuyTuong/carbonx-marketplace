package com.carbonx.identity.domain.company;

import com.carbonx.identity.domain.shared.DomainValidationException;

import java.time.Instant;

/** Company aggregate: business profile bound to one COMPANY user plus its KYC decision. */
public final class Company {

    private final Long id;
    private final Long userId;
    private final String businessName;
    private final String taxCode;
    private final String businessLicense;
    private final String address;
    private final KycStatus kycStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Company(Long id, Long userId, String businessName, String taxCode, String businessLicense,
                    String address, KycStatus kycStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.businessName = businessName;
        this.taxCode = taxCode;
        this.businessLicense = businessLicense;
        this.address = address;
        this.kycStatus = kycStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Company submit(Long userId, String businessName, String taxCode, String address) {
        return submit(userId, businessName, taxCode, null, address);
    }

    public static Company submit(Long userId, String businessName, String taxCode, String businessLicense,
                                 String address) {
        if (userId == null) {
            throw new DomainValidationException("userId is required");
        }
        return new Company(null, userId, requireText(businessName, "businessName"),
                trimToNull(taxCode), trimToNull(businessLicense), trimToNull(address),
                KycStatus.PENDING, null, null);
    }

    public static Company rehydrate(Long id, Long userId, String businessName, String taxCode, String address,
                                    KycStatus kycStatus, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, userId, businessName, taxCode, null, address, kycStatus, createdAt, updatedAt);
    }

    public static Company rehydrate(Long id, Long userId, String businessName, String taxCode, String businessLicense,
                                    String address, KycStatus kycStatus, Instant createdAt, Instant updatedAt) {
        return new Company(id, userId, requireText(businessName, "businessName"),
                trimToNull(taxCode), trimToNull(businessLicense), trimToNull(address),
                kycStatus == null ? KycStatus.PENDING : kycStatus, createdAt, updatedAt);
    }

    /** Resubmission always returns the dossier to PENDING, even after a rejection. */
    public Company updateProfile(String businessName, String taxCode, String address) {
        return updateProfile(businessName, taxCode, this.businessLicense, address);
    }

    public Company updateProfile(String businessName, String taxCode, String businessLicense, String address) {
        return new Company(id, userId, requireText(businessName, "businessName"),
                trimToNull(taxCode), trimToNull(businessLicense), trimToNull(address),
                KycStatus.PENDING, createdAt, updatedAt);
    }

    public Company approve() {
        return new Company(id, userId, businessName, taxCode, businessLicense, address, KycStatus.APPROVED,
                createdAt, updatedAt);
    }

    public Company reject() {
        return new Company(id, userId, businessName, taxCode, businessLicense, address, KycStatus.REJECTED,
                createdAt, updatedAt);
    }

    public Company withId(Long assignedId) {
        return new Company(assignedId, userId, businessName, taxCode, businessLicense, address, kycStatus,
                createdAt, updatedAt);
    }

    public boolean isApproved() {
        return kycStatus == KycStatus.APPROVED;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public String businessName() {
        return businessName;
    }

    public String taxCode() {
        return taxCode;
    }

    public String businessLicense() {
        return businessLicense;
    }

    public String address() {
        return address;
    }

    public KycStatus kycStatus() {
        return kycStatus;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
