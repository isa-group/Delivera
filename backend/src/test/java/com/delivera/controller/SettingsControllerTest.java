package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.settings.*;
import com.delivera.model.OrderPriority;

import com.delivera.service.SettingsService;
import com.delivera.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock private SettingsService settingsService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private SecurityUtils securityUtils;
    @InjectMocks private SettingsController controller;

    private static final UUID COMPANY_ID = UUID.randomUUID();

    private static SettingsResponse settingsResp() {
        return new SettingsResponse(UUID.randomUUID(), "Org", "org-1", COMPANY_ID, "Co", "LOGISTICS", OrderPriority.NORMAL, false);
    }

    @Test
    void getSettings_returns200() {
        when(settingsService.getSettings()).thenReturn(settingsResp());
        assertThat(controller.getSettings().getStatusCode().value()).isEqualTo(200);
        verify(settingsService).getSettings();
    }

    @Test
    void updateOrg_returns200() {
        OrgUpdateRequest req = new OrgUpdateRequest("New Org", "new-org");
        when(settingsService.updateOrg(req)).thenReturn(settingsResp());
        assertThat(controller.updateOrg(req).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCompany_returns200() {
        CompanyUpdateRequest req = new CompanyUpdateRequest("New Co", "LOGISTICS", OrderPriority.HIGH, false);
        when(settingsService.updateCompany(req)).thenReturn(settingsResp());
        assertThat(controller.updateCompany(req).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getMyCompanies_returns200() {
        CompanySummary cs = new CompanySummary(COMPANY_ID, "Co", "LOGISTICS", null, OrderPriority.NORMAL, false);
        when(settingsService.getMyCompanies()).thenReturn(List.of(cs));
        var resp = controller.getMyCompanies();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void createCompany_returns201() {
        CompanyCreateRequest req = new CompanyCreateRequest("Nueva", "LOGISTICS");
        CompanySummary expected = new CompanySummary(UUID.randomUUID(), "Nueva", "LOGISTICS", null, OrderPriority.NORMAL, false);
        when(settingsService.createCompany(req)).thenReturn(expected);
        var resp = controller.createCompany(req);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isSameAs(expected);
    }

    @Test
    void getSubscription_returns200() {
        SubscriptionUsageResponse usage = new SubscriptionUsageResponse("FREE", "Free",
                new SubscriptionUsageResponse.ResourceUsage(1, 3),
                new SubscriptionUsageResponse.ResourceUsage(2, 5),
                new SubscriptionUsageResponse.ResourceUsage(10, 100),
                new SubscriptionUsageResponse.ResourceUsage(5, 50),
                new SubscriptionUsageResponse.ResourceUsage(1, 1));
        when(securityUtils.getCurrentCompanyId()).thenReturn(COMPANY_ID);
        when(subscriptionService.getUsage(COMPANY_ID)).thenReturn(usage);
        assertThat(controller.getSubscription().getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void changePlan_returns200() {
        ChangePlanRequest req = new ChangePlanRequest("PRO", false);
        when(securityUtils.getCurrentCompanyId()).thenReturn(COMPANY_ID);
        when(subscriptionService.changePlan(COMPANY_ID, "PRO", false)).thenReturn(null);
        assertThat(controller.changePlan(req).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCompanyLogo_returns200() {
        CompanySummary expected = new CompanySummary(COMPANY_ID, "Co", "LOGISTICS", "base64data", OrderPriority.NORMAL, false);
        when(settingsService.updateCompanyLogo("base64data")).thenReturn(expected);
        var resp = controller.updateCompanyLogo(Map.of("data", "base64data"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(expected);
    }

    @Test
    void deleteCompany_returns204() {
        UUID id = UUID.randomUUID();
        doNothing().when(settingsService).deleteCompany(id, true);
        assertThat(controller.deleteCompany(id, true).getStatusCode().value()).isEqualTo(204);
        verify(settingsService).deleteCompany(id, true);
    }
}
