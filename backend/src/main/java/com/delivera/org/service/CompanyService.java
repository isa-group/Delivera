package com.delivera.org.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}
