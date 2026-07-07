package com.delivera.service;

import com.delivera.depot.model.UnitType;
import com.delivera.model.OrderStatus;
import com.delivera.repository.LoyalUserRepository;
import com.delivera.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private LoyalUserRepository loyalUserRepository;
    @InjectMocks private ActivityService activityService;

    @ParameterizedTest
    @ValueSource(strings = {"TODAY", "WEEK", "MONTH"})
    void getMetrics_returnsCountsForAllPeriods(String period) {
        UUID companyId = UUID.randomUUID();
        when(orderRepository.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(10L);
        when(orderRepository.countByCompanyIdAndStatusAndCreatedAtAfter(eq(companyId), eq(OrderStatus.DELIVERED), any(Instant.class))).thenReturn(6L);
        when(orderRepository.countByCompanyIdAndStatusAndCreatedAtAfter(eq(companyId), eq(OrderStatus.CANCELLED), any(Instant.class))).thenReturn(1L);
        when(orderRepository.countByCompanyIdAndStatusNotIn(eq(companyId), any(Collection.class))).thenReturn(3L);
        when(loyalUserRepository.countByCompaniesIdAndCreatedAtAfter(eq(companyId), any(Instant.class))).thenReturn(2L);

        var result = activityService.getMetrics(companyId, period);

        assertThat(result.period()).isEqualTo(period);
        assertThat(result.totalOrders()).isEqualTo(10);
        assertThat(result.completedOrders()).isEqualTo(6);
        assertThat(result.activeOrders()).isEqualTo(3);
    }

    @Test
    void getOrdersByDay_mapsBothDateTypes() {
        UUID companyId = UUID.randomUUID();
        Object[] row1 = new Object[]{LocalDate.of(2026, 4, 1), 5L};
        Object[] row2 = new Object[]{Date.valueOf("2026-04-02"), 3L};
        List<Object[]> rows = List.of(row1, row2);
        when(orderRepository.countByDayForCompany(eq(companyId), any(Instant.class))).thenReturn(rows);

        var result = activityService.getOrdersByDay(companyId, "MONTH");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).count()).isEqualTo(5);
    }

    @Test
    void getUnitRanking_mapsUnitEntries() {
        UUID companyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        Object[] row = new Object[]{unitId, "Warehouse", UnitType.WAREHOUSE, 7L};
        List<Object[]> rows = java.util.Collections.singletonList(row);
        when(orderRepository.countByOriginUnitForCompany(eq(companyId), any(Instant.class))).thenReturn(rows);

        var result = activityService.getUnitRanking(companyId, "WEEK");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).unitName()).isEqualTo("Warehouse");
    }
}
