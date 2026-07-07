package com.delivera.service;

import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.dto.admin.GlobalMetrics;
import com.delivera.dto.admin.OrganizationSummary;
import com.delivera.order.repository.OrderEventRepository;
import com.delivera.order.repository.OrderMessageRepository;
import com.delivera.order.repository.OrderRepository;
import com.delivera.repository.*;
import com.delivera.worker.repository.WorkerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private OperationalUnitRepository unitRepository;
    @Mock private OrderMessageRepository orderMessageRepository;
    @Mock private OrderEventRepository orderEventRepository;
    @Mock private LoyalUserCompanyRepository loyalUserCompanyRepository;
    @Mock private LoyalUserRepository loyalUserRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @InjectMocks private AdminService adminService;

    @Test
    void listOrganizations_returnsEmptyList() {
        when(organizationRepository.findAllSummaries()).thenReturn(List.of());

        List<OrganizationSummary> result = adminService.listOrganizations();

        assertThat(result).isEmpty();
    }

    @Test
    void listOrganizations_returnsSummariesWithMetrics() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(organizationRepository.findAllSummaries()).thenReturn(
                List.<Object[]>of(new Object[]{id, "Test Org", "test-org", now, 3L, 10L, 50L}));

        List<OrganizationSummary> result = adminService.listOrganizations();

        assertThat(result).hasSize(1);
        OrganizationSummary summary = result.get(0);
        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.name()).isEqualTo("Test Org");
        assertThat(summary.handle()).isEqualTo("test-org");
        assertThat(summary.companyCount()).isEqualTo(3);
        assertThat(summary.workerCount()).isEqualTo(10);
        assertThat(summary.orderCount()).isEqualTo(50);
    }

    @Test
    void listOrganizations_multipleOrgs() {
        Instant now = Instant.now();
        when(organizationRepository.findAllSummaries()).thenReturn(List.<Object[]>of(
                new Object[]{UUID.randomUUID(), "Org A", "org-a", now, 1L, 2L, 5L},
                new Object[]{UUID.randomUUID(), "Org B", "org-b", now, 1L, 2L, 5L}));

        List<OrganizationSummary> result = adminService.listOrganizations();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Org A");
        assertThat(result.get(1).name()).isEqualTo("Org B");
    }

    @Test
    void getGlobalMetrics_returnsCorrectCounts() {
        when(organizationRepository.countTenants()).thenReturn(5L);
        when(companyRepository.countTenants()).thenReturn(12L);
        when(orderRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(100L);
        when(userRepository.count()).thenReturn(43L);

        GlobalMetrics result = adminService.getGlobalMetrics();

        assertThat(result.totalOrganizations()).isEqualTo(5);
        assertThat(result.totalCompanies()).isEqualTo(12);
        assertThat(result.totalOrdersThisMonth()).isEqualTo(100);
        assertThat(result.totalActiveUsers()).isEqualTo(42);
    }

    @Test
    void getGlobalMetrics_zeroCounts() {
        when(organizationRepository.countTenants()).thenReturn(0L);
        when(companyRepository.countTenants()).thenReturn(0L);
        when(orderRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(0L);
        when(userRepository.count()).thenReturn(1L);

        GlobalMetrics result = adminService.getGlobalMetrics();

        assertThat(result.totalOrganizations()).isZero();
        assertThat(result.totalCompanies()).isZero();
        assertThat(result.totalOrdersThisMonth()).isZero();
        assertThat(result.totalActiveUsers()).isZero();
    }
}
