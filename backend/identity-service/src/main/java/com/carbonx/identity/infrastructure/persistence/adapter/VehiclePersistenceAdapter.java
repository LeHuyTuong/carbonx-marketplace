package com.carbonx.identity.infrastructure.persistence.adapter;

import com.carbonx.identity.application.port.out.VehicleRepositoryPort;
import com.carbonx.identity.domain.vehicle.PlateNumber;
import com.carbonx.identity.domain.vehicle.Vehicle;
import com.carbonx.identity.domain.vehicle.VehicleStatus;
import com.carbonx.identity.infrastructure.persistence.entity.VehicleJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.VehicleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adapts the Vehicle aggregate to the {@code vehicles} table. */
@Component
@RequiredArgsConstructor
public class VehiclePersistenceAdapter implements VehicleRepositoryPort {

    private final VehicleJpaRepository repository;

    @Override
    public Optional<Vehicle> findById(Long id) {
        return repository.findById(id).map(VehiclePersistenceAdapter::toDomain);
    }

    @Override
    public List<Vehicle> findByEvOwnerId(Long evOwnerId) {
        return repository.findByEvOwnerIdOrderByCreatedAtDesc(evOwnerId).stream()
                .map(VehiclePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Vehicle> findByCompanyIdAndPlateNumbers(Long companyId, Collection<String> plateNumbers) {
        if (plateNumbers == null || plateNumbers.isEmpty()) {
            return List.of();
        }
        return repository.findByCompanyIdAndPlateNumberIn(companyId, plateNumbers).stream()
                .map(VehiclePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Vehicle> findByCompanyId(Long companyId) {
        return repository.findByCompanyIdOrderByIdDesc(companyId).stream()
                .map(VehiclePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Vehicle> findAll(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).getContent().stream()
                .map(VehiclePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByPlateNumber(String plateNumber) {
        return repository.existsByPlateNumber(plateNumber);
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        return toDomain(repository.save(toEntity(vehicle)));
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private static Vehicle toDomain(VehicleJpaEntity entity) {
        return Vehicle.rehydrate(entity.getId(), entity.getEvOwnerId(), entity.getCompanyId(),
                new PlateNumber(entity.getPlateNumber()), entity.getBrand(), entity.getModel(),
                entity.getDocumentUrl(), VehicleStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static VehicleJpaEntity toEntity(Vehicle vehicle) {
        return VehicleJpaEntity.builder()
                .id(vehicle.id())
                .evOwnerId(vehicle.evOwnerId())
                .companyId(vehicle.companyId())
                .plateNumber(vehicle.plateNumber().value())
                .brand(vehicle.brand())
                .model(vehicle.model())
                .documentUrl(vehicle.documentUrl())
                .status(vehicle.status().name())
                .build();
    }
}
