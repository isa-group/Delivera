package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.repository.AuthRepository;
import com.delivera.exception.WorkerNotFoundException;

@Service
public class AuthInternalService {

    private final AuthRepository repository;

    public AuthInternalService(AuthRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DeliveraOrgContext getContextByUserId(UUID userId) {
         
        return repository
            .findOrgContextByUserId(userId, PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .orElseThrow( () -> new WorkerNotFoundException() );


    }

    @Transactional(readOnly = true)
    public DeliveraOrgContext getContextByUserIdAndByCompanyId(UUID userId, UUID companyId) {
         
        return repository
            .findOrgContextByUserIdAndCompanyId(userId,companyId)
            .orElseThrow( () -> new WorkerNotFoundException() );


    }

}
