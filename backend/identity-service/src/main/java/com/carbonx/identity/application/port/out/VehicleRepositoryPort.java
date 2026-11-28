package com.carbonx.identity.application.port.out;

import com.carbonx.identity.domain.vehicle.Vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Outbound persistence port for the Vehicle aggregate. */
public interface VehicleRepositoryPort {
    Optional<Vehicle> findById(Long id);

    List<Vehicle> findByEvOwnerId(Long evOwnerId);

    List<Vehicle> findByCompanyIdAndPlateNumbers(Long companyId, Collection<String> plateNumbers);

    List<Vehicle> findByCompanyId(Long companyId);

    List<Vehicle> findAll(int page, int size);

    boolean existsByPlateNumber(String plateNumber);

    Vehicle save(Vehicle vehicle);

    void deleteById(Long id);
}
