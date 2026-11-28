package com.carbonx.identity.application.port.in;

import java.util.List;

/** Inbound Use-Case Port: EV Owner Vehicle Registration & Fleet Queries. */
public interface VehicleUseCase {
    VehicleView registerVehicle(Long evOwnerId, RegisterVehicleCommand command);

    VehicleView updateVehicle(Long evOwnerId, Long vehicleId, UpdateVehicleCommand command);

    void deleteVehicle(Long actorUserId, String actorRole, Long vehicleId);

    List<VehicleView> listMyVehicles(Long evOwnerId);

    List<VehicleView> listCompanyVehicles(Long companyId);

    List<VehicleView> listAllVehiclesForAdmin(int page, int size);

    record RegisterVehicleCommand(Long companyId, String plateNumber, String brand, String model, String documentUrl) {}

    record UpdateVehicleCommand(Long companyId, String brand, String model, String documentUrl) {}

    record VehicleView(Long id, Long evOwnerId, Long companyId, String companyName, String plateNumber, String brand, String model, String documentUrl, String status) {}
}
