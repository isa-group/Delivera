package com.delivera.org.service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.org.dto.IdNameProjection;
import com.delivera.org.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public Map<UUID,String> getOrganizationsNames() {
        return organizationRepository.findNames()
        .stream()
        .collect(
            Collectors.toMap(
                IdNameProjection::getId,
                IdNameProjection::getName
            )
        );
    }

}
