package com.delivera.controller;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.activity.ActivityMetricsResponse;
import com.delivera.dto.activity.UnitRankingEntry;

import com.delivera.service.ActivityService;
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
class ActivityControllerTest {

    @Mock private ActivityService activityService;
    @Mock private SecurityUtils securityUtils;
    @InjectMocks private ActivityController controller;

    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Test
    void getMetrics_returns200() {
        ActivityMetricsResponse resp = new ActivityMetricsResponse("MONTH", 10, 8, 1, 2, 3);
        when(securityUtils.getCurrentCompanyId()).thenReturn(COMPANY_ID);
        when(activityService.getMetrics(COMPANY_ID, "MONTH")).thenReturn(resp);
        assertThat(controller.getMetrics("MONTH").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getOrdersByDay_returns200() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(COMPANY_ID);
        when(activityService.getOrdersByDay(COMPANY_ID, "WEEK")).thenReturn(List.of());
        assertThat(controller.getOrdersByDay("WEEK").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getUnitRanking_returns200() {
        UnitRankingEntry entry = new UnitRankingEntry(UUID.randomUUID(), "Almacén", "WAREHOUSE", 5);
        when(securityUtils.getCurrentCompanyId()).thenReturn(COMPANY_ID);
        when(activityService.getUnitRanking(COMPANY_ID, "TODAY")).thenReturn(List.of(entry));
        var resp = controller.getUnitRanking("TODAY");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }
}
