package com.delivera.org.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.service.UnitClient;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.CompanyHasActiveOrdersException;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.HandleConflictException;
import com.delivera.exception.UserNotFoundException;
import com.delivera.model.*;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.dto.CompanyCreateRequest;
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
import com.delivera.service.SubscriptionService;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.delivera.order.model.OrderStatus.IN_TRANSIT;
import static com.delivera.order.model.OrderStatus.PENDING;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SettingsService {

    private final CompanyRepository companyRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final UnitClient unitClient;
    private final ActivityTypeRepository activityTypeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SecurityUtils securityUtils;
    private final SubscriptionService subscriptionService;
    private final AppConfigService appConfigService;

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

    @Transactional
    public CompanySummary createCompany(CompanyCreateRequest req) {
        Company current = currentCompany();
        subscriptionService.checkCompanyLimit(current.getId());
        Organization org = current.getOrganization();

        Company newCompany = new Company();
        newCompany.setOrganization(org);
        newCompany.setName(req.name());
        newCompany.setActivityType(activityTypeRepository.getReferenceById(req.activityType()));
        newCompany.setPlan(subscriptionPlanRepository.getReferenceById("FREE"));
        companyRepository.save(newCompany);

        String email = securityUtils.getCurrentEmail();
        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        Worker worker = new Worker();
        worker.setUser(user);
        worker.setCompany(newCompany);
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        workerRepository.save(worker);

        return new CompanySummary(newCompany.getId(), newCompany.getName(), newCompany.getActivityType().getCode(), null, null, false);
    }

    @Transactional
    public void deleteCompany(UUID companyId, boolean force) {
        Company current = currentCompany();
        Company target = companyRepository.findById(companyId).orElseThrow(() -> new ForbiddenException("Company not found"));

        if (!target.getOrganization().getId().equals(current.getOrganization().getId())) {
            throw new ForbiddenException("Company does not belong to current organization");
        }
        if (companyId.equals(current.getId())) {
            throw new ForbiddenException("Cannot delete the company you are currently logged into");
        }
        if (!force && orderRepository.existsByCompanyIdAndStatusIn(companyId, List.of(PENDING, IN_TRANSIT))) {
            throw new CompanyHasActiveOrdersException(companyId);
        }

        orderRepository.deleteEventsByCompanyId(companyId);
        orderRepository.deleteByCompanyId(companyId);
        for (LoyalUser lu : loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(companyId)) {
            lu.unlinkFrom(companyId);
            if (lu.getCompanyLinks().isEmpty()) loyalUserRepository.delete(lu);
            else loyalUserRepository.save(lu);
        }
        // TODO: WORKER REPOSITORY DOESN'T DELETE USER ACCOUNT IF IT'S THE WORKER ASSOCIATE TO THAT ACCOUNT.
        unitClient.deleteAllFromCompany(companyId);
        workerRepository.deleteAll(workerRepository.findByCompanyId(companyId));
        companyRepository.delete(target);
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
