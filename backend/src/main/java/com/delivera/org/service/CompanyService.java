package com.delivera.org.service;


import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.org.dto.CompanyAdminProjection;
import com.delivera.org.dto.IdNameProjection;
import com.delivera.org.dto.OrgCheckRequest;
import com.delivera.org.repository.CompanyRepository;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public Boolean checkCompanyBelongsToOrganization(OrgCheckRequest request) {
        return companyRepository.existsByIdAndOrganizationId(request.getCompanyId(), request.getOrgId());
    }

    @Transactional(readOnly = true)
    public Map<UUID,String> getCompanyNames(Set<UUID> companyIds) {
        companyIds.removeIf(Objects::isNull);
        return companyRepository.findNamesById(companyIds)
        .stream()
        .collect(
            Collectors.toMap(
                IdNameProjection::getId,
                IdNameProjection::getName
            )
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID,String> getByCompany(UUID organizationId) {
        return companyRepository.findNamesByOrgId(organizationId)
        .stream()
        .collect(
            Collectors.toMap(
                IdNameProjection::getId,
                IdNameProjection::getName
            )
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID,Map<String,String>> getCompanyAndOrgNames() {
        return companyRepository.findCompanyAndOrgNames()
        .stream()
        .collect(
            Collectors.toMap(
                CompanyAdminProjection::getCompanyId,
                entry -> Map.of(
                    "companyName", entry.getCompanyName(), 
                    "orgName", entry.getOrgName()
                )
            )
        );
    }


}
