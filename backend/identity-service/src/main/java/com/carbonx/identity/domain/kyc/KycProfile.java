package com.carbonx.identity.domain.kyc;

import com.carbonx.identity.domain.company.KycStatus;
import com.carbonx.identity.domain.shared.DomainValidationException;

import java.time.Instant;

/** EV owner identity dossier (CCCD/passport) reviewed before the owner can register vehicles. */
public final class KycProfile {

    private final Long id;
    private final Long evOwnerId;
    private final String fullName;
    private final String documentType;
    private final String documentNumber;
    private final String phone;
    private final String country;
    private final String address;
    private final String birthDate;
    private final String gender;
    private final KycStatus kycStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    private KycProfile(Long id, Long evOwnerId, String fullName, String documentType, String documentNumber,
                       String phone, String country, String address, String birthDate, String gender,
                       KycStatus kycStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.evOwnerId = evOwnerId;
        this.fullName = fullName;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.phone = phone;
        this.country = country;
        this.address = address;
        this.birthDate = birthDate;
        this.gender = gender;
        this.kycStatus = kycStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static KycProfile submit(Long evOwnerId, String documentType, String documentNumber, String phone) {
        return submit(evOwnerId, null, documentType, documentNumber, phone, null, null, null, null);
    }

    public static KycProfile submit(Long evOwnerId, String fullName, String documentType, String documentNumber,
                                    String phone, String country, String address, String birthDate, String gender) {
        if (evOwnerId == null) {
            throw new DomainValidationException("evOwnerId is required");
        }
        return new KycProfile(null, evOwnerId, trimToNull(fullName), requireText(documentType, "documentType"),
                requireText(documentNumber, "documentNumber"), trimToNull(phone),
                trimToNull(country), trimToNull(address), trimToNull(birthDate), trimToNull(gender),
                KycStatus.PENDING, null, null);
    }

    public static KycProfile rehydrate(Long id, Long evOwnerId, String documentType, String documentNumber,
                                       String phone, KycStatus kycStatus, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, evOwnerId, null, documentType, documentNumber, phone, null, null, null, null,
                kycStatus, createdAt, updatedAt);
    }

    public static KycProfile rehydrate(Long id, Long evOwnerId, String fullName, String documentType,
                                       String documentNumber, String phone, String country, String address,
                                       String birthDate, String gender, KycStatus kycStatus,
                                       Instant createdAt, Instant updatedAt) {
        return new KycProfile(id, evOwnerId, trimToNull(fullName), requireText(documentType, "documentType"),
                requireText(documentNumber, "documentNumber"), trimToNull(phone),
                trimToNull(country), trimToNull(address), trimToNull(birthDate), trimToNull(gender),
                kycStatus == null ? KycStatus.PENDING : kycStatus, createdAt, updatedAt);
    }

    public KycProfile updateDocument(String documentType, String documentNumber, String phone) {
        return updateDocument(this.fullName, documentType, documentNumber, phone,
                this.country, this.address, this.birthDate, this.gender);
    }

    public KycProfile updateDocument(String fullName, String documentType, String documentNumber, String phone,
                                     String country, String address, String birthDate, String gender) {
        return new KycProfile(id, evOwnerId, trimToNull(fullName), requireText(documentType, "documentType"),
                requireText(documentNumber, "documentNumber"), trimToNull(phone),
                trimToNull(country), trimToNull(address), trimToNull(birthDate), trimToNull(gender),
                KycStatus.PENDING, createdAt, updatedAt);
    }

    public KycProfile withId(Long assignedId) {
        return new KycProfile(assignedId, evOwnerId, fullName, documentType, documentNumber, phone,
                country, address, birthDate, gender, kycStatus, createdAt, updatedAt);
    }

    public Long id() {
        return id;
    }

    public Long evOwnerId() {
        return evOwnerId;
    }

    public String fullName() {
        return fullName;
    }

    public String documentType() {
        return documentType;
    }

    public String documentNumber() {
        return documentNumber;
    }

    public String phone() {
        return phone;
    }

    public String country() {
        return country;
    }

    public String address() {
        return address;
    }

    public String birthDate() {
        return birthDate;
    }

    public String gender() {
        return gender;
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
