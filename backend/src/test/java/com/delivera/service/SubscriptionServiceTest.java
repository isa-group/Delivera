package com.delivera.service;

import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.SubscriptionLimitException;
import com.delivera.model.SubscriptionPlan;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.repository.*;
import com.delivera.worker.repository.WorkerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private OperationalUnitRepository unitRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private LoyalUserRepository loyalUserRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @InjectMocks private SubscriptionService subscriptionService;

    private UUID companyId;
    private Company company;
    private SubscriptionPlan freePlan;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        freePlan = buildPlan("FREE", 1, 3, 5, 50, 20);
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        company = new Company();
        company.setId(companyId);
        company.setPlan(freePlan);
        company.setOrganization(org);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
    }

    @Test
    void checkUnitLimit_belowLimit_passes() {
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        assertThatCode(() -> subscriptionService.checkUnitLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void checkUnitLimit_atLimit_throws() {
        when(unitRepository.countByCompanyId(companyId)).thenReturn(3L);
        assertThatThrownBy(() -> subscriptionService.checkUnitLimit(companyId))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("units");
    }

    @Test
    void checkOrderLimit_belowLimit_passes() {
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(49L);
        assertThatCode(() -> subscriptionService.checkOrderLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void checkOrderLimit_atLimit_throws() {
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(50L);
        assertThatThrownBy(() -> subscriptionService.checkOrderLimit(companyId))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("orders");
    }

    @Test
    void checkLoyalUserLimit_belowLimit_passes() {
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(0L);
        assertThatCode(() -> subscriptionService.checkLoyalUserLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void checkLoyalUserLimit_atLimit_throws() {
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(20L);
        assertThatThrownBy(() -> subscriptionService.checkLoyalUserLimit(companyId))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("loyal_users");
    }

    @Test
    void checkWorkerLimit_belowLimit_passes() {
        when(workerRepository.countByCompanyId(companyId)).thenReturn(4L);
        assertThatCode(() -> subscriptionService.checkWorkerLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void checkWorkerLimit_atLimit_throws() {
        when(workerRepository.countByCompanyId(companyId)).thenReturn(5L);
        assertThatThrownBy(() -> subscriptionService.checkWorkerLimit(companyId))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("workers");
    }

    @Test
    void checkCompanyLimit_belowLimit_passes() {
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(0L);
        assertThatCode(() -> subscriptionService.checkCompanyLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void checkCompanyLimit_atLimit_throws() {
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(1L);
        assertThatThrownBy(() -> subscriptionService.checkCompanyLimit(companyId))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("companies");
    }

    @Test
    void getUsage_returnsCurrentCounts() {
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(3L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(10L);
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(5L);
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(1L);

        var usage = subscriptionService.getUsage(companyId);

        assertThat(usage.planCode()).isEqualTo("FREE");
        assertThat(usage.units().current()).isEqualTo(2);
        assertThat(usage.units().max()).isEqualTo(3);
        assertThat(usage.workers().current()).isEqualTo(3);
        assertThat(usage.ordersThisMonth().current()).isEqualTo(10);
        assertThat(usage.loyalUsers().current()).isEqualTo(5);
        assertThat(usage.companies().current()).isEqualTo(1);
    }

    @Test
    void unlimited_plan_never_blocks() {
        SubscriptionPlan pro = buildPlan("PRO", -1, -1, -1, -1, -1);
        company.setPlan(pro);
        when(unitRepository.countByCompanyId(companyId)).thenReturn(9999L);
        assertThatCode(() -> subscriptionService.checkUnitLimit(companyId)).doesNotThrowAnyException();
    }

    @Test
    void changePlan_upgrade_succeeds() {
        SubscriptionPlan basic = buildPlan("BASIC", 5, 10, 15, 200, 100);
        when(subscriptionPlanRepository.findById("BASIC")).thenReturn(Optional.of(basic));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(3L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(10L);
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(5L);
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(1L);

        var usage = subscriptionService.changePlan(companyId, "BASIC", false);
        assertThat(usage.planCode()).isEqualTo("BASIC");
    }

    @Test
    void changePlan_downgrade_blockedByUnits_throws() {
        SubscriptionPlan free = buildPlan("FREE", 1, 3, 5, 50, 20);
        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(5L);

        assertThatThrownBy(() -> subscriptionService.changePlan(companyId, "FREE", false))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("units");
    }

    @Test
    void changePlan_downgrade_blockedByWorkers_throws() {
        SubscriptionPlan free = buildPlan("FREE", 1, 3, 5, 50, 20);
        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(6L);

        assertThatThrownBy(() -> subscriptionService.changePlan(companyId, "FREE", false))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("workers");
    }

    @Test
    void changePlan_downgrade_blockedByOrders_throws() {
        SubscriptionPlan free = buildPlan("FREE", 1, 3, 5, 50, 20);
        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(3L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(51L);

        assertThatThrownBy(() -> subscriptionService.changePlan(companyId, "FREE", false))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("orders");
    }

    @Test
    void changePlan_downgrade_blockedByLoyalUsers_throws() {
        SubscriptionPlan free = buildPlan("FREE", 1, 3, 5, 50, 20);
        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(3L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(10L);
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(21L);

        assertThatThrownBy(() -> subscriptionService.changePlan(companyId, "FREE", false))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("loyal_users");
    }

    @Test
    void changePlan_downgrade_blockedByCompanies_throws() {
        SubscriptionPlan free = buildPlan("FREE", 1, 3, 5, 50, 20);
        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(workerRepository.countByCompanyId(companyId)).thenReturn(3L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(10L);
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(5L);
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(2L);

        assertThatThrownBy(() -> subscriptionService.changePlan(companyId, "FREE", false))
                .isInstanceOf(SubscriptionLimitException.class)
                .hasMessageContaining("companies");
    }

    @Test
    void changePlan_force_deletesExcessResources() {
        SubscriptionPlan free = buildPlan("FREE", 1, 0, 0, 50, 0);
        com.delivera.worker.model.Worker admin = new com.delivera.worker.model.Worker();
        admin.setId(UUID.randomUUID());
        admin.setRole(com.delivera.worker.model.WorkerRole.COMPANY_ADMIN);
        com.delivera.worker.model.Worker analyst = new com.delivera.worker.model.Worker();
        analyst.setId(UUID.randomUUID());
        analyst.setRole(com.delivera.worker.model.WorkerRole.ANALYST);
        com.delivera.model.LoyalUser lu = new com.delivera.model.LoyalUser();
        com.delivera.depot.model.OperationalUnit unit = new com.delivera.depot.model.OperationalUnit();
        lu.setId(UUID.randomUUID());
        lu.linkFor(company);
        unit.setId(UUID.randomUUID());

        when(subscriptionPlanRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(workerRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)).thenReturn(java.util.List.of(admin, analyst));
        when(loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(companyId)).thenReturn(java.util.List.of(lu));
        when(unitRepository.countByCompanyId(companyId)).thenReturn(1L);
        when(unitRepository.findByCompanyIdWithNoOrdersOrderByCreatedAtDesc(companyId)).thenReturn(java.util.List.of(unit));
        when(companyRepository.findByOrganizationIdOrderByCreatedAtDesc(company.getOrganization().getId())).thenReturn(java.util.List.of(company));
        when(workerRepository.countByCompanyId(companyId)).thenReturn(0L);
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(0L);
        when(loyalUserRepository.countByCompanyId(companyId)).thenReturn(0L);
        when(companyRepository.countByOrganizationId(company.getOrganization().getId())).thenReturn(1L);

        subscriptionService.changePlan(companyId, "FREE", true);

        verify(workerRepository).deleteAll(any());
        verify(loyalUserRepository).save(lu);
        verify(unitRepository).delete(unit);
    }

    // --- helpers ---

    private SubscriptionPlan buildPlan(String code, int maxCompanies, int maxUnits,
                                       int maxWorkers, int maxOrders, int maxLoyalUsers) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setCode(code);
        p.setName(code);
        p.setMaxCompanies(maxCompanies);
        p.setMaxUnits(maxUnits);
        p.setMaxWorkers(maxWorkers);
        p.setMaxOrdersPerMonth(maxOrders);
        p.setMaxLoyalUsers(maxLoyalUsers);
        return p;
    }
}
