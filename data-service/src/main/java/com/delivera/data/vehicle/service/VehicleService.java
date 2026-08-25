package com.delivera.data.vehicle.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.transaction.annotation.Compensable;
import com.delivera.client.transaction.compensation.Compensations;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.exception.UnitNotFoundException;
import com.delivera.data.exception.VehicleNotFoundException;
import com.delivera.data.exception.VehiclePlateConflictException;
import com.delivera.data.space.service.SpaceVehicles;
import com.delivera.data.vehicle.dto.VehicleRequest;
import com.delivera.data.vehicle.dto.VehicleResponse;
import com.delivera.data.vehicle.model.Vehicle;
import com.delivera.data.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final OperationalUnitRepository unitRepository;
    private final SecurityUtils securityUtils;
    private final SpaceVehicles spaceVehicles;

    @Compensable
    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        // WE ASUME THAT company exists if a company's unit exists

        if (vehicleRepository.existsByCompanyIdAndPlate(companyId, request.plate())) {
            throw new VehiclePlateConflictException();
        }

        OperationalUnit depot = unitRepository.findByIdAndCompanyId(request.depotId(), companyId)
                .orElseThrow(() -> new UnitNotFoundException(request.depotId()));

        Vehicle vehicle = new Vehicle();
        vehicle.setCompanyId(depot.getCompanyId());
        vehicle.setDepot(depot);
        vehicle.setPlate(request.plate());
        vehicle.setCapacity(request.capacity());

        String orgId = depot.getOrgId().toString();
        spaceVehicles.addWithRollBack(orgId);
        try {
            return VehicleResponse.from(vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException e) {
            throw new VehiclePlateConflictException();
        }
    }

    @Compensable
    @Transactional
    public VehicleResponse createSeed(UUID companyId,VehicleRequest request) {
        if (vehicleRepository.existsByCompanyIdAndPlate(companyId, request.plate())) {
            throw new VehiclePlateConflictException();
        }
        OperationalUnit depot = unitRepository.findByIdAndCompanyId(request.depotId(), companyId)
                .orElseThrow(() -> new UnitNotFoundException(request.depotId()));
        

      
        Vehicle vehicle = new Vehicle();
        vehicle.setCompanyId(depot.getCompanyId());
        vehicle.setDepot(depot);
        vehicle.setPlate(request.plate());
        vehicle.setCapacity(request.capacity());

        String orgId = depot.getOrgId().toString();
        spaceVehicles.addWithRollBack(orgId);

        try {
            return VehicleResponse.from(vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException e) {
            throw new VehiclePlateConflictException();
        }
    }

    @Transactional
    public VehicleResponse update(UUID vehicleId, VehicleRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        if (vehicleRepository.existsByCompanyIdAndPlateAndIdNot(companyId, request.plate(), vehicleId)) {
            throw new VehiclePlateConflictException();
        }

        OperationalUnit depot = unitRepository.findByIdAndCompanyId(request.depotId(), companyId)
                .orElseThrow(() -> new UnitNotFoundException(request.depotId()));

        vehicle.setDepot(depot);
        vehicle.setPlate(request.plate());
        vehicle.setCapacity(request.capacity());

        try {
            return VehicleResponse.from(vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException e) {
            throw new VehiclePlateConflictException();
        }
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return getByCompany(companyId);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getByCompany(UUID companyId) {
        return vehicleRepository.findAllByCompanyId(companyId).stream()
                .map(VehicleResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public VehicleResponse getDetail(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new VehicleNotFoundException(id));
        return VehicleResponse.from(vehicle);
    }

    @Compensable
    @Transactional
    public void delete(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String orgId = securityUtils.getCurrentOrgId().toString();
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        spaceVehicles.deleteWithRollBack(orgId);
        vehicleRepository.delete(vehicle);
    }
}
