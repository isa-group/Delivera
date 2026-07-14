package com.delivera.service;

import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.SubscriptionLimitException;
import com.delivera.model.LoyalUser;
import com.delivera.model.SubscriptionPlan;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.dto.SubscriptionUsageResponse;
import com.delivera.org.dto.SubscriptionUsageResponse.ResourceUsage;
import com.delivera.org.model.Company;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.repository.*;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final CompanyRepository companyRepository;
    private final OperationalUnitRepository unitRepository;
    private final WorkerRepository workerRepository;
    private final OrderRepository orderRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionService(CompanyRepository companyRepository,
                               OperationalUnitRepository unitRepository,
                               WorkerRepository workerRepository,
                               OrderRepository orderRepository,
                               LoyalUserRepository loyalUserRepository,
                               SubscriptionPlanRepository subscriptionPlanRepository) {
        this.companyRepository = companyRepository;
        this.unitRepository = unitRepository;
        this.workerRepository = workerRepository;
        this.orderRepository = orderRepository;
        this.loyalUserRepository = loyalUserRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional(readOnly = true)
    public void checkUnitLimit(UUID companyId) {
        SubscriptionPlan plan = getPlan(companyId);
        if (!plan.allows(unitRepository.countByCompanyId(companyId), plan.getMaxUnits())) {
            throw new SubscriptionLimitException("units");
        }
    }

    @Transactional(readOnly = true)
    public void checkWorkerLimit(UUID companyId) {
        SubscriptionPlan plan = getPlan(companyId);
        if (!plan.allows(workerRepository.countByCompanyId(companyId), plan.getMaxWorkers())) {
            throw new SubscriptionLimitException("workers");
        }
    }

    @Transactional(readOnly = true)
    public void checkOrderLimit(UUID companyId) {
        SubscriptionPlan plan = getPlan(companyId);
        if (!plan.allows(orderRepository.countByCompanyIdAndCreatedAtAfter(companyId, startOfMonth()), plan.getMaxOrdersPerMonth())) {
            throw new SubscriptionLimitException("orders");
        }
    }

    @Transactional(readOnly = true)
    public void checkLoyalUserLimit(UUID companyId) {
        SubscriptionPlan plan = getPlan(companyId);
        if (!plan.allows(loyalUserRepository.countByCompanyId(companyId), plan.getMaxLoyalUsers())) {
            throw new SubscriptionLimitException("loyal_users");
        }
    }

    @Transactional(readOnly = true)
    public void checkCompanyLimit(UUID companyId) {
        Company company = getCompany(companyId);
        SubscriptionPlan plan = company.getPlan();
        if (!plan.allows(companyRepository.countByOrganizationId(company.getOrganization().getId()), plan.getMaxCompanies())) {
            throw new SubscriptionLimitException("companies");
        }
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageResponse getUsage(UUID companyId) {
        return buildUsageResponse(getCompany(companyId));
    }

    @Transactional
    public SubscriptionUsageResponse changePlan(UUID companyId, String planCode, boolean force) {
        SubscriptionPlan newPlan = subscriptionPlanRepository.findById(planCode)
                .orElseThrow(CompanyContextException::new);
        Company company = getCompany(companyId);
        if (force) {
            deleteExcessResources(companyId, newPlan);
        } else {
            validateLimits(companyId, company, newPlan);
        }
        company.setPlan(newPlan);
        companyRepository.save(company);
        return buildUsageResponse(company);
    }

    private SubscriptionUsageResponse buildUsageResponse(Company company) {
        UUID companyId = company.getId();
        SubscriptionPlan plan = company.getPlan();
        Instant som = startOfMonth();
        return new SubscriptionUsageResponse(
                plan.getCode(),
                plan.getName(),
                new ResourceUsage(unitRepository.countByCompanyId(companyId), plan.getMaxUnits()),
                new ResourceUsage(workerRepository.countByCompanyId(companyId), plan.getMaxWorkers()),
                new ResourceUsage(orderRepository.countByCompanyIdAndCreatedAtAfter(companyId, som), plan.getMaxOrdersPerMonth()),
                new ResourceUsage(loyalUserRepository.countByCompanyId(companyId), plan.getMaxLoyalUsers()),
                new ResourceUsage(companyRepository.countByOrganizationId(company.getOrganization().getId()), plan.getMaxCompanies())
        );
    }

    private void validateLimits(UUID companyId, Company company, SubscriptionPlan plan) {
        Instant som = startOfMonth();
        if (plan.getMaxUnits() != -1 && unitRepository.countByCompanyId(companyId) > plan.getMaxUnits())
            throw new SubscriptionLimitException("units");
        if (plan.getMaxWorkers() != -1 && workerRepository.countByCompanyId(companyId) > plan.getMaxWorkers())
            throw new SubscriptionLimitException("workers");
        if (plan.getMaxOrdersPerMonth() != -1 && orderRepository.countByCompanyIdAndCreatedAtAfter(companyId, som) > plan.getMaxOrdersPerMonth())
            throw new SubscriptionLimitException("orders");
        if (plan.getMaxLoyalUsers() != -1 && loyalUserRepository.countByCompanyId(companyId) > plan.getMaxLoyalUsers())
            throw new SubscriptionLimitException("loyal_users");
        if (plan.getMaxCompanies() != -1 && companyRepository.countByOrganizationId(company.getOrganization().getId()) > plan.getMaxCompanies())
            throw new SubscriptionLimitException("companies");
    }

    private void deleteExcessResources(UUID companyId, SubscriptionPlan newPlan) {
        deleteExcessWorkers(companyId, newPlan);
        deleteExcessLoyalUsers(companyId, newPlan);
        deleteExcessUnits(companyId, newPlan);
        deleteExcessCompanies(companyId, newPlan);
    }

    private void deleteExcessWorkers(UUID companyId, SubscriptionPlan newPlan) {
        if (newPlan.getMaxWorkers() == -1) return;
        List<Worker> workers = workerRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        long excess = (long) workers.size() - newPlan.getMaxWorkers();
        if (excess <= 0) return;
        List<Worker> reversed = workers.reversed();
        List<Worker> toDelete = new ArrayList<>();
        long adminCount = workers.stream().filter(w -> w.getRole() == WorkerRole.COMPANY_ADMIN).count();
        for (Worker w : reversed) {
            if (excess <= 0) break;
            if (w.getRole() != WorkerRole.COMPANY_ADMIN) {
                toDelete.add(w); excess--;
            } else if (adminCount > 1) {
                toDelete.add(w); excess--; adminCount--;
            }
        }
        workerRepository.deleteAll(toDelete);
    }

    private void deleteExcessLoyalUsers(UUID companyId, SubscriptionPlan newPlan) {
        if (newPlan.getMaxLoyalUsers() == -1) return;
        List<LoyalUser> loyalUsers = loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(companyId);
        long excess = (long) loyalUsers.size() - newPlan.getMaxLoyalUsers();
        for (int i = 0; i < excess && i < loyalUsers.size(); i++) {
            LoyalUser lu = loyalUsers.get(i);
            lu.unlinkFrom(companyId);
            loyalUserRepository.save(lu);
        }
    }

    private void deleteExcessUnits(UUID companyId, SubscriptionPlan newPlan) {
        if (newPlan.getMaxUnits() == -1) return;
        long excess = unitRepository.countByCompanyId(companyId) - newPlan.getMaxUnits();
        if (excess <= 0) return;
        List<OperationalUnit> deletable = unitRepository.findByCompanyIdWithNoOrdersOrderByCreatedAtDesc(companyId);
        for (int i = 0; i < excess && i < deletable.size(); i++) {
            unitRepository.delete(deletable.get(i));
        }
    }

    private void deleteExcessCompanies(UUID companyId, SubscriptionPlan newPlan) {
        if (newPlan.getMaxCompanies() == -1) return;
        Company current = companyRepository.findById(companyId).orElseThrow();
        UUID orgId = current.getOrganization().getId();
        List<Company> companies = companyRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        long excess = (long) companies.size() - newPlan.getMaxCompanies();
        for (Company c : companies) {
            if (excess <= 0) break;
            if (!c.getId().equals(companyId)) {
                purgeCompany(c);
                excess--;
            }
        }
    }

    private void purgeCompany(Company c) {
        UUID cId = c.getId();
        orderRepository.deleteEventsByCompanyId(cId);
        orderRepository.deleteByCompanyId(cId);
        for (LoyalUser lu : loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(cId)) {
            lu.unlinkFrom(cId);
            if (lu.getCompanyLinks().isEmpty()) loyalUserRepository.delete(lu);
            else loyalUserRepository.save(lu);
        }
        unitRepository.deleteByCompanyId(cId);
        workerRepository.deleteByCompanyId(cId);
        companyRepository.delete(c);
    }

    private SubscriptionPlan getPlan(UUID companyId) {
        return getCompany(companyId).getPlan();
    }

    private Company getCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(CompanyContextException::new);
    }

    private Instant startOfMonth() {
        return LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }
}
