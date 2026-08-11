package com.delivera.org.service;


import com.delivera.auth.service.AuthClient;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.exception.ClientException;
import com.delivera.client.transaction.annotation.Compensable;
import com.delivera.client.transaction.compensation.Compensations;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.CompanyHasActiveOrdersException;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.HandleConflictException;
import com.delivera.exception.UserNotFoundException;
import com.delivera.model.*;
import com.delivera.org.dto.CompanyCreateRequest;
import com.delivera.org.dto.CompanySettingsDTO;
import com.delivera.org.dto.CompanySummary;
import com.delivera.org.dto.CompanyUpdateRequest;
import com.delivera.org.dto.OrgUpdateRequest;
import com.delivera.org.dto.SettingsResponse;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.org.repository.OrganizationRepository;
import com.delivera.repository.*;
import com.delivera.service.AppConfigService;
import com.delivera.space.service.SpaceCompanies;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SettingsService {

    private final CompanyRepository companyRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SecurityUtils securityUtils;
    private final AppConfigService appConfigService;
    private final SettingsClient settingsClient;
    private final AuthClient authClient;
    private final SpaceCompanies spaceCompanies;

    private Company currentCompany() {
        return companyRepository.findById(securityUtils.getCurrentCompanyId())
                .orElseThrow(CompanyContextException::new);
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return buildSettingsResponse(currentCompany());
    }

    @Transactional
    public SettingsResponse updateOrg(OrgUpdateRequest req) {
        Company c = currentCompany();
        Organization o = c.getOrganization();
        if (!o.getHandle().equals(req.handle()) && organizationRepository.existsByHandleAndIdNot(req.handle(), o.getId())) {
            throw new HandleConflictException(req.handle(), null);
        }
        o.setName(req.name());
        o.setHandle(req.handle());
        organizationRepository.save(o);
        return buildSettingsResponse(c);
    }

    @Transactional
    public SettingsResponse updateCompany(CompanyUpdateRequest req) {
        Company c = currentCompany();
        c.setName(req.name());
        c.setActivityType(activityTypeRepository.getReferenceById(req.activityType()));
        c.setDefaultPriority(req.defaultPriority());
        c.setDefaultPriorityLocked(req.defaultPriorityLocked());
        companyRepository.save(c);
        return buildSettingsResponse(c);
    }

    private SettingsResponse buildSettingsResponse(Company c) {
        Organization o = c.getOrganization();
        return new SettingsResponse(o.getId(), o.getName(), o.getHandle(), c.getId(), c.getName(), c.getActivityType().getCode(), c.getDefaultPriority(), c.isDefaultPriorityLocked());
    }

    @Compensable
    @Transactional
    //@SpaceTransaction
    public CompanySummary createCompany(CompanyCreateRequest req) {
      
        Company current = currentCompany();
        Organization org = current.getOrganization();

        spaceCompanies.addCompany(org.getId().toString());

        /*SpaceTransactions.registerRollback(() -> {
            spaceCompanies.deleteCompany(org.getId().toString());
        });*/
        Compensations.registerRollback(() -> {
            spaceCompanies.deleteCompany(org.getId().toString());
        });

        Company newCompany = new Company();
        newCompany.setOrganization(org);
        newCompany.setName(req.name());
        newCompany.setActivityType(activityTypeRepository.getReferenceById(req.activityType()));
        newCompany.setPlan(subscriptionPlanRepository.getReferenceById("FREE"));
        Company savedCompany = companyRepository.save(newCompany);

        String email = securityUtils.getCurrentEmail();
        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        Worker worker = new Worker();
        worker.setUser(user);
        worker.setCompany(newCompany);
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        workerRepository.save(worker);

        settingsClient.createSettings(new CompanySettingsDTO(savedCompany.getId(),null, false));

        
        return new CompanySummary(newCompany.getId(), newCompany.getName(), newCompany.getActivityType().getCode(), null, null, false);
    }

    @Compensable
    @Transactional
    //@SpaceTransaction
    public void deleteCompany(UUID companyId, boolean force) {
        Company current = currentCompany();
        Company target = companyRepository.findById(companyId).orElseThrow(() -> new ForbiddenException("Company not found"));
        UUID targetOrgId = target.getOrganization().getId();

        if (!target.getOrganization().getId().equals(current.getOrganization().getId())) {
            throw new ForbiddenException("Company does not belong to current organization");
        }
        if (companyId.equals(current.getId())) {
            throw new ForbiddenException("Cannot delete the company you are currently logged into");
        }

        spaceCompanies.deleteCompany(targetOrgId.toString());
        Compensations.registerRollback(() -> targetOrgId.toString());
       
        for (LoyalUser lu : loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(companyId)) {
            lu.unlinkFrom(companyId);
            if (lu.getCompanyLinks().isEmpty()) loyalUserRepository.delete(lu);
            else loyalUserRepository.save(lu);
        }
        Set<UUID> userIds = workerRepository.findAccountsToDelete(target.getOrganization().getId(), target.getId());
        workerRepository.deleteByCompanyId(companyId);
        companyRepository.delete(target);
        try {
            settingsClient.deleteAllByCompany(companyId, force);
        } catch (ClientException e) {
            throw new CompanyHasActiveOrdersException(companyId);
        }
        authClient.deleteUsers(userIds);

    }

    @Transactional(readOnly = true)
    public List<CompanySummary> getMyCompanies() {
        String email = securityUtils.getCurrentEmail();
        UUID orgId = currentCompany().getOrganization().getId();
        return workerRepository.findByUserEmailAndOrgId(email, orgId).stream()
                .map(w -> new CompanySummary(w.getCompany().getId(), w.getCompany().getName(), w.getCompany().getActivityType().getCode(), w.getCompany().getLogoData(), w.getCompany().getDefaultPriority(), w.getCompany().isDefaultPriorityLocked()))
                .toList();
    }

    @Transactional
    public CompanySummary updateCompanyLogo(String logoData) {
        appConfigService.checkUploadSize(logoData);
        Company c = currentCompany();
        c.setLogoData(logoData);
        companyRepository.save(c);
        return new CompanySummary(c.getId(), c.getName(), c.getActivityType().getCode(), c.getLogoData(), c.getDefaultPriority(), c.isDefaultPriorityLocked());
    }
}
