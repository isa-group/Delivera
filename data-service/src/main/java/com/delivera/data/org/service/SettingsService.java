package com.delivera.data.org.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.data.exception.ForbiddenException;
import com.delivera.data.exception.SettingsAlreadyExistsException;
import com.delivera.data.exception.SettingsNotFoundException;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.repository.SettingsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository repository;


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
