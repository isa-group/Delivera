package com.delivera.org.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.org.dto.CompanyName;
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
        return companyRepository.findNamesById(companyIds)
        .stream()
        .collect(
            Collectors.toMap(
                CompanyName::getCompanyId,
                CompanyName::getName
            )
        );
    }

}
