package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.repository.AuthRepository;
import com.delivera.worker.model.WorkerRole;

@Service
public class AuthInternalService {

    private static final WorkerRole LOYAL_USER_ROLE = WorkerRole.LOYAL_USER;

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
            .orElse(
                new DeliveraOrgContext(
                null, LOYAL_USER_ROLE,null
                , null, null,null
            ));


    }

    @Transactional(readOnly = true)
    public DeliveraOrgContext getContextByUserIdAndByCompanyId(UUID userId, UUID companyId) {
         
        return repository
            .findOrgContextByUserIdAndCompanyId(userId,companyId)
            .orElse(
                new DeliveraOrgContext(
                null, LOYAL_USER_ROLE,null
                , null, null,null
            ));


    }

}
