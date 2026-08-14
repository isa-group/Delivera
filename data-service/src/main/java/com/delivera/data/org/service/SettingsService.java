package com.delivera.data.org.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.space.service.AbstractSpaceFeature;
import com.delivera.client.transaction.annotation.Compensable;
import com.delivera.client.transaction.compensation.Compensations;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.exception.CompanyHasActiveOrdersException;
import com.delivera.data.exception.SettingsAlreadyExistsException;
import com.delivera.data.exception.SettingsNotFoundException;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.repository.OrderEventRepository;
import com.delivera.data.order.repository.OrderMessageRepository;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.repository.SettingsRepository;
import com.delivera.data.space.service.SpaceUnits;
import com.delivera.data.space.service.SpaceVehicles;
import com.delivera.data.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository repository;
    private final OrderRepository orderRepository;
    private final OperationalUnitRepository unitRepository;
    private final VehicleRepository vehicleRepository;
    private final WorkerRepository workerRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderMessageRepository orderMessageRepository;

    private final SpaceUnits spaceUnits;
    private final SpaceVehicles spaceVehicles;

    private final SecurityUtils securityUtils;

    @Compensable
    @Transactional
    public void deleteCompany(UUID companyId, Boolean confirmation) {
        //String orgId = securityUtils.getCurrentOrgId().toString();
        CompanySettings settings = get(companyId);
        String orgId = settings.getOrgId().toString();

        Boolean pendingOrders = orderRepository.existsByCompanyIdAndStatusIn(
            companyId,
            List.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT)
        );
        if (pendingOrders && !confirmation) {
            throw new CompanyHasActiveOrdersException(companyId);
        }

        Integer numVehicles = vehicleRepository.deleteByCompanyId(companyId);
        spaceDelete(spaceVehicles, orgId, numVehicles);

        orderEventRepository.deleteByCompanyId(companyId);
        orderMessageRepository.deleteByCompanyId(companyId);
        orderRepository.deleteByCompanyId(companyId);
        orderRepository.nullifyDestinationByCompanyId(companyId);
        workerRepository.deleteByCompanyId(companyId);

        Integer numUnits = unitRepository.deleteByCompanyId(companyId);
        spaceDelete(spaceUnits,orgId,numUnits);
        
        repository.deleteById(companyId);
    }

    @Compensable
    @Transactional
    public void deleteOrganization(String orgId ,Set<UUID> companyIds) {

        Integer numVehicles = vehicleRepository.deleteByCompanyIds(companyIds);
        spaceDelete(spaceVehicles, orgId, numVehicles);

        orderEventRepository.deleteByCompanyIds(companyIds);
        orderMessageRepository.deleteByCompanyIds(companyIds);
        orderRepository.deleteByCompanyIds(companyIds);
        orderRepository.nullifyDestinationByCompanyIds(companyIds);
        workerRepository.deleteByCompanyIds(companyIds);
        
        Integer numUnits = unitRepository.deleteByCompanyIds(companyIds);
        spaceDelete(spaceUnits, orgId, numUnits);

        repository.deleteByCompanyIds(companyIds);
    }

    @Transactional(readOnly = true)
    public CompanySettings get(UUID companyId) {   
        CompanySettings settings = repository.findById(
            companyId
        ).orElseThrow(
            () -> new SettingsNotFoundException(companyId)
        );     
        return settings;
    }

    @Transactional
    public CompanySettings create(CompanySettingsDTO request)  {
        if (repository.existsById(request.getCompanyId())) {
            throw new SettingsAlreadyExistsException(
                request.getCompanyId()
            );
        }

        CompanySettings settings = mapForCreate(request);

        return repository.save(settings);
    }

    @Transactional
    public CompanySettings update(CompanySettingsDTO request)  {
        CompanySettings settings = repository.findById(
            request.getCompanyId()
        ).orElseThrow(
            () -> new SettingsNotFoundException(request.getCompanyId())
        );

        map(settings,request);

        return repository.save(settings);
    }
    
    
    private CompanySettings map(
        CompanySettings settings, 
        CompanySettingsDTO request
    )  {
        if (settings == null || request == null) {
            throw new IllegalArgumentException(
                "COMPANY:SETTINGS AND REQUEST MUST BE PRESENTS"
            );
        }
        settings.setDefaultPriority(
            request.getDefaultPriority()
        );
        settings.setDefaultPriorityLocked(
            request.isDefaultPriorityLocked()
        );
        return settings;
    }

    private CompanySettings mapForCreate(
        CompanySettingsDTO request
    ) {
        CompanySettings settings = new CompanySettings();
        settings.setId(request.getCompanyId());
        settings.setOrgId(request.getOrgId());
        return map(settings,request);
    }


    private void spaceDelete(
        AbstractSpaceFeature feature,
        String orgId, 
        Integer quantity
    ){
        if (quantity > 0) {
            feature.deleteAllWithRollBack(orgId, quantity);
        }
    }


}
