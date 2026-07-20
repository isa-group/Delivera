package com.delivera.vehicle.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.UnitNotFoundException;
import com.delivera.exception.VehicleNotFoundException;
import com.delivera.exception.VehiclePlateConflictException;
import com.delivera.org.model.Company;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.vehicle.dto.VehicleRequest;
import com.delivera.vehicle.dto.VehicleResponse;
import com.delivera.vehicle.model.Vehicle;
import com.delivera.vehicle.repository.VehicleRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final OperationalUnitRepository unitRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtils securityUtils;

    public VehicleService(VehicleRepository vehicleRepository,
                          OperationalUnitRepository unitRepository,
                          CompanyRepository companyRepository,
                          SecurityUtils securityUtils) {
        this.vehicleRepository = vehicleRepository;
        this.unitRepository = unitRepository;
        this.companyRepository = companyRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(CompanyContextException::new);

        if (vehicleRepository.existsByCompanyIdAndPlate(companyId, request.plate())) {
            throw new VehiclePlateConflictException();
        }

        OperationalUnit depot = unitRepository.findByIdAndCompanyId(request.depotId(), companyId)
                .orElseThrow(() -> new UnitNotFoundException(request.depotId()));

        Vehicle vehicle = new Vehicle();
        vehicle.setCompany(company);
        vehicle.setDepot(depot);
        vehicle.setPlate(request.plate());
        vehicle.setCapacity(request.capacity());

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

    @Transactional
    public void delete(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new VehicleNotFoundException(id));
        vehicleRepository.delete(vehicle);
    }
}
