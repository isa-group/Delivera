package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.admin.GlobalMetrics;
import com.delivera.dto.admin.OrganizationSummary;

import com.delivera.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private AdminService adminService;
    @Mock private SecurityUtils securityUtils;
    @InjectMocks private AdminController controller;

    @Test
    void listOrganizations_returns200WithList() {
        OrganizationSummary s = new OrganizationSummary(UUID.randomUUID(), "Org", "org", null, 1, 2, 10);
        when(adminService.listOrganizations()).thenReturn(List.of(s));
        var resp = controller.listOrganizations();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void getGlobalMetrics_returns200WithMetrics() {
        GlobalMetrics metrics = new GlobalMetrics(5, 15, 100, 50);
        when(adminService.getGlobalMetrics()).thenReturn(metrics);
        var resp = controller.getGlobalMetrics();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(metrics);
    }
}
