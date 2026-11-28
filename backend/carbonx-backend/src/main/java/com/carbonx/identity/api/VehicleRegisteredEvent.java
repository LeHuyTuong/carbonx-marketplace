package com.carbonx.identity.api;

import com.carbonx.shared.domain.DomainEvent;

public record VehicleRegisteredEvent(
        Long vehicleId,
        Long evOwnerId,
        Long companyId,
        String plateNumber,
        String brand,
        String model
) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "Vehicle";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(vehicleId);
    }

    @Override
    public String eventType() {
        return "identity.vehicle.registered";
    }
}
