package com.delivera.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.dto.settings.CompanyCreateRequest;
import com.delivera.dto.settings.CompanyUpdateRequest;
import com.delivera.dto.settings.OrgUpdateRequest;
import com.delivera.exception.CompanyHasActiveOrdersException;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.HandleConflictException;
import com.delivera.model.*;
import com.delivera.order.repository.OrderRepository;
import com.delivera.repository.*;
import com.delivera.repository.ActivityTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private LoyalUserRepository loyalUserRepository;
    @Mock
    private OperationalUnitRepository operationalUnitRepository;
    @Mock
    private ActivityTypeRepository activityTypeRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private AppConfigService appConfigService;
    @InjectMocks
    private SettingsService settingsService;

    private UUID companyId;
    private UUID orgId;
    private Organization organization;
    private Company company;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        organization = new Organization();
        organization.setId(orgId);
        organization.setName("TestOrg");
        organization.setHandle("test-org");

        company = new Company();
        company.setId(companyId);
        company.setName("TestCompany");
        ActivityType at = new ActivityType(); at.setCode("TRANSPORT"); company.setActivityType(at);
        company.setOrganization(organization);

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
    }

    @Test
    void getSettings_returnsCurrentOrgAndCompany() {
        var result = settingsService.getSettings();
        assertThat(result.companyName()).isEqualTo("TestCompany");
        assertThat(result.orgHandle()).isEqualTo("test-org");
    }

    @Test
    void updateOrg_success() {
        OrgUpdateRequest req = new OrgUpdateRequest("NewOrg", "new-org");
        when(organizationRepository.existsByHandleAndIdNot("new-org", orgId)).thenReturn(false);
        when(organizationRepository.save(organization)).thenReturn(organization);

        var result = settingsService.updateOrg(req);
        assertThat(result).isNotNull();
        verify(organizationRepository).save(organization);
    }

    @Test
    void createCompany_success() {
        CompanyCreateRequest req = new CompanyCreateRequest("NewCompany", "FOOD");
        User user = new User();
        user.setEmail("admin@test.com");
        Company newCompany = new Company();
        newCompany.setId(UUID.randomUUID());
        newCompany.setName("NewCompany");
        ActivityType at2 = new ActivityType(); at2.setCode("FOOD"); newCompany.setActivityType(at2);
        newCompany.setOrganization(organization);

        when(securityUtils.getCurrentEmail()).thenReturn("admin@test.com");
        ActivityType food = new ActivityType(); food.setCode("FOOD");
        when(activityTypeRepository.getReferenceById("FOOD")).thenReturn(food);
        when(companyRepository.save(any())).thenReturn(newCompany);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(workerRepository.save(any())).thenReturn(new Worker());

        var result = settingsService.createCompany(req);
        assertThat(result.name()).isEqualTo("NewCompany");
    }

    @Test
    void deleteCompany_success() {
        Company target = new Company();
        target.setId(UUID.randomUUID());
        target.setOrganization(organization);

        when(companyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(orderRepository.existsByCompanyIdAndStatusIn(any(), any())).thenReturn(false);
        when(loyalUserRepository.findByCompanyIdOrderByLinkCreatedAtDesc(any())).thenReturn(List.of());
        when(operationalUnitRepository.findAllByCompanyId(any())).thenReturn(List.of());
        when(workerRepository.findByCompanyId(any())).thenReturn(List.of());

        settingsService.deleteCompany(target.getId(), false);
        verify(companyRepository).delete(target);
    }

    @Test
    void updateOrg_handleConflict_throws() {
        OrgUpdateRequest req = new OrgUpdateRequest("NewOrg", "taken");
        when(organizationRepository.existsByHandleAndIdNot("taken", orgId)).thenReturn(true);
        assertThatThrownBy(() -> settingsService.updateOrg(req))
                .isInstanceOf(HandleConflictException.class);
    }

    @Test
    void updateCompany_success() {
        CompanyUpdateRequest req = new CompanyUpdateRequest("Renamed", "FOOD", null, false);
        ActivityType food = new ActivityType(); food.setCode("FOOD");
        when(activityTypeRepository.getReferenceById("FOOD")).thenReturn(food);
        when(companyRepository.save(company)).thenReturn(company);
        assertThat(settingsService.updateCompany(req)).isNotNull();
        verify(companyRepository).save(company);
    }

    @Test
    void deleteCompany_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> settingsService.deleteCompany(id, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteCompany_differentOrg_throws() {
        Organization otherOrg = new Organization(); otherOrg.setId(UUID.randomUUID());
        Company target = new Company(); target.setId(UUID.randomUUID()); target.setOrganization(otherOrg);
        when(companyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> settingsService.deleteCompany(target.getId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteCompany_sameAsCurrent_throws() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        assertThatThrownBy(() -> settingsService.deleteCompany(companyId, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteCompany_activeOrders_throws() {
        Company target = new Company(); target.setId(UUID.randomUUID()); target.setOrganization(organization);
        when(companyRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(orderRepository.existsByCompanyIdAndStatusIn(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> settingsService.deleteCompany(target.getId(), false))
                .isInstanceOf(CompanyHasActiveOrdersException.class);
    }

    @Test
    void getMyCompanies_returnsList() {
        when(securityUtils.getCurrentEmail()).thenReturn("admin@test.com");
        Worker w = new Worker(); w.setCompany(company);
        when(workerRepository.findByUserEmailAndOrgId("admin@test.com", orgId)).thenReturn(List.of(w));
        assertThat(settingsService.getMyCompanies()).hasSize(1);
    }

    @Test
    void updateCompanyLogo_success() {
        when(companyRepository.save(company)).thenReturn(company);
        var result = settingsService.updateCompanyLogo("data:image/png;base64,XXX");
        assertThat(result.logoData()).isEqualTo("data:image/png;base64,XXX");
    }

}
