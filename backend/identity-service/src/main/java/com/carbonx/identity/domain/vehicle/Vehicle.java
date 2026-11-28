package com.carbonx.identity.domain.vehicle;

import com.carbonx.identity.domain.shared.DomainValidationException;

import java.time.Instant;

/** Electric vehicle registered by an EV owner under a company running an approved carbon project. */
public final class Vehicle {

    private final Long id;
    private final Long evOwnerId;
    private final Long companyId;
    private final PlateNumber plateNumber;
    private final String brand;
    private final String model;
    private final String documentUrl;
    private final VehicleStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Vehicle(Long id, Long evOwnerId, Long companyId, PlateNumber plateNumber, String brand,
                    String model, String documentUrl, VehicleStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.evOwnerId = evOwnerId;
        this.companyId = companyId;
        this.plateNumber = plateNumber;
        this.brand = brand;
        this.model = model;
        this.documentUrl = documentUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Vehicle register(Long evOwnerId, Long companyId, PlateNumber plateNumber, String brand,
                                   String model, String documentUrl) {
        if (evOwnerId == null) {
            throw new DomainValidationException("evOwnerId is required");
        }
        if (companyId == null) {
            throw new DomainValidationException("companyId is required");
        }
        if (plateNumber == null) {
            throw new DomainValidationException("plateNumber is required");
        }
        return new Vehicle(null, evOwnerId, companyId, plateNumber, requireText(brand, "brand"),
                requireText(model, "model"), trimToNull(documentUrl), VehicleStatus.ACTIVE, null, null);
    }

    public static Vehicle rehydrate(Long id, Long evOwnerId, Long companyId, PlateNumber plateNumber, String brand,
                                    String model, String documentUrl, VehicleStatus status,
                                    Instant createdAt, Instant updatedAt) {
        return new Vehicle(id, evOwnerId, companyId, plateNumber, requireText(brand, "brand"),
                requireText(model, "model"), trimToNull(documentUrl),
                status == null ? VehicleStatus.ACTIVE : status, createdAt, updatedAt);
    }

    public Vehicle withId(Long assignedId) {
        return new Vehicle(assignedId, evOwnerId, companyId, plateNumber, brand, model, documentUrl, status,
                createdAt, updatedAt);
    }

    public Vehicle updateDetails(Long targetCompanyId, PlateNumber nextPlate, String nextBrand,
                                 String nextModel, String nextDocumentUrl) {
        return new Vehicle(
                id,
                evOwnerId,
                targetCompanyId == null ? companyId : targetCompanyId,
                nextPlate == null ? plateNumber : nextPlate,
                requireText(nextBrand, "brand"),
                requireText(nextModel, "model"),
                nextDocumentUrl != null ? trimToNull(nextDocumentUrl) : documentUrl,
                status,
                createdAt,
                updatedAt);
    }

    public Vehicle deactivate() {
        return new Vehicle(id, evOwnerId, companyId, plateNumber, brand, model, documentUrl,
                VehicleStatus.INACTIVE, createdAt, updatedAt);
    }

    public boolean isOwnedBy(Long userId) {
        return evOwnerId.equals(userId);
    }

    public Long id() {
        return id;
    }

    public Long evOwnerId() {
        return evOwnerId;
    }

    public Long companyId() {
        return companyId;
    }

    public PlateNumber plateNumber() {
        return plateNumber;
    }

    public String brand() {
        return brand;
    }

    public String model() {
        return model;
    }

    public String documentUrl() {
        return documentUrl;
    }

    public VehicleStatus status() {
        return status;
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
