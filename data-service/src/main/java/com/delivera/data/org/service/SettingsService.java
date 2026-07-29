package com.delivera.data.org.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    @Transactional
    public void deleteCompany(UUID companyId, Boolean confirmation) {
        Boolean pendingOrders = orderRepository.existsByCompanyIdAndStatusIn(
            companyId,
            List.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT)
        );
        if (pendingOrders && !confirmation) {
            throw new CompanyHasActiveOrdersException(companyId);
        }

        vehicleRepository.deleteByCompanyId(companyId);
        orderEventRepository.deleteByCompanyId(companyId);
        orderMessageRepository.deleteByCompanyId(companyId);
        orderRepository.deleteByCompanyId(companyId);
        workerRepository.deleteByCompanyId(companyId);
        unitRepository.deleteByCompanyId(companyId);
        repository.deleteById(companyId);
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
        return map(settings,request);
    }

}
