package com.carbonx.mrv.domain.project;

import com.carbonx.mrv.domain.shared.DomainValidationException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * VM0038 project: the methodology constants applied to every emission report of the project.
 * Only administrators create projects; companies apply to an open project.
 */
public record Project(
        Long id,
        String title,
        String description,
        BigDecimal emissionFactor,
        BigDecimal bufferRate,
        BigDecimal uncertaintyRate,
        BigDecimal leakageRate,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    /** Grid emission factor (tCO2e/MWh) used by the legacy CarbonX methodology. */
    public static final BigDecimal DEFAULT_EMISSION_FACTOR = new BigDecimal("0.676600");
    public static final BigDecimal DEFAULT_BUFFER_RATE = new BigDecimal("0.0500");
    public static final BigDecimal DEFAULT_UNCERTAINTY_RATE = new BigDecimal("0.0300");
    public static final BigDecimal DEFAULT_LEAKAGE_RATE = new BigDecimal("0.0200");

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    public Project {
        if (title == null || title.isBlank()) {
            throw new DomainValidationException("Project title is required");
        }
        title = title.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new DomainValidationException("Project title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (description != null) {
            description = description.trim();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                throw new DomainValidationException(
                        "Project description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
            }
            if (description.isEmpty()) {
                description = null;
            }
        }
        emissionFactor = positive(emissionFactor, "emissionFactor", DEFAULT_EMISSION_FACTOR);
        bufferRate = rate(bufferRate, "bufferRate", DEFAULT_BUFFER_RATE);
        uncertaintyRate = rate(uncertaintyRate, "uncertaintyRate", DEFAULT_UNCERTAINTY_RATE);
        leakageRate = rate(leakageRate, "leakageRate", DEFAULT_LEAKAGE_RATE);
        status = status == null ? ProjectStatus.OPEN : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static Project create(String title, String description, BigDecimal emissionFactor,
                                 BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate) {
        return create(title, description, emissionFactor, bufferRate, uncertaintyRate, leakageRate, ProjectStatus.OPEN);
    }

    public static Project create(String title, String description, BigDecimal emissionFactor,
                                 BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate,
                                 ProjectStatus status) {
        return new Project(null, title, description, emissionFactor, bufferRate, uncertaintyRate,
                leakageRate, status == null ? ProjectStatus.OPEN : status, Instant.now(), Instant.now());
    }

    public Project update(String nextTitle, String nextDescription, BigDecimal nextEmissionFactor,
                          BigDecimal nextBufferRate, BigDecimal nextUncertaintyRate, BigDecimal nextLeakageRate,
                          ProjectStatus nextStatus) {
        return new Project(id, nextTitle, nextDescription,
                nextEmissionFactor == null ? emissionFactor : nextEmissionFactor,
                nextBufferRate == null ? bufferRate : nextBufferRate,
                nextUncertaintyRate == null ? uncertaintyRate : nextUncertaintyRate,
                nextLeakageRate == null ? leakageRate : nextLeakageRate,
                nextStatus == null ? status : nextStatus,
                createdAt, Instant.now());
    }

    public boolean isOpen() {
        return status == ProjectStatus.OPEN;
    }

    /** Gross tCO2e of a reporting period before buffer, uncertainty and leakage deductions. */
    public BigDecimal grossCo2Kg(BigDecimal totalEnergyKwh) {
        if (totalEnergyKwh == null) {
            return BigDecimal.ZERO;
        }
        return totalEnergyKwh.multiply(emissionFactor);
    }

    private static BigDecimal positive(BigDecimal value, String field, BigDecimal fallback) {
        BigDecimal resolved = value == null ? fallback : value;
        if (resolved.signum() <= 0) {
            throw new DomainValidationException(field + " must be greater than zero");
        }
        return resolved;
    }

    private static BigDecimal rate(BigDecimal value, String field, BigDecimal fallback) {
        BigDecimal resolved = value == null ? fallback : value;
        if (resolved.signum() < 0 || resolved.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainValidationException(field + " must be between 0 and 1");
        }
        return resolved;
    }
}
