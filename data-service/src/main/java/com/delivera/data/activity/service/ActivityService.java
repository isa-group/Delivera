package com.delivera.data.activity.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.data.activity.dto.ActivityMetricsResponse;
import com.delivera.data.activity.dto.OrdersByDayEntry;
import com.delivera.data.activity.dto.UnitRankingEntry;
import com.delivera.data.depot.model.UnitType;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final List<OrderStatus> TERMINAL = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;

    

    @Transactional(readOnly = true)
    public ActivityMetricsResponse getMetrics(UUID companyId, String period) {
        Instant from = periodStart(period);
        return new ActivityMetricsResponse(
                period,
                orderRepository.countByCompanyIdAndCreatedAtAfter(companyId, from),
                orderRepository.countByCompanyIdAndStatusAndCreatedAtAfter(companyId, OrderStatus.DELIVERED, from),
                orderRepository.countByCompanyIdAndStatusAndCreatedAtAfter(companyId, OrderStatus.CANCELLED, from),
                orderRepository.countByCompanyIdAndStatusNotIn(companyId, TERMINAL)               
        );
    }

    @Transactional(readOnly = true)
    public List<OrdersByDayEntry> getOrdersByDay(UUID companyId, String period) {
        Instant from = periodStart(period);
        return orderRepository.countByDayForCompany(companyId, from).stream()
                .map(row -> {
                    LocalDate date = row[0] instanceof LocalDate d ? d : ((Date) row[0]).toLocalDate();
                    long count = ((Number) row[1]).longValue();
                    return new OrdersByDayEntry(date, count);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnitRankingEntry> getUnitRanking(UUID companyId, String period) {
        Instant from = periodStart(period);
        return orderRepository.countByOriginUnitForCompany(companyId, from).stream()
                .map(row -> new UnitRankingEntry(
                        (UUID) row[0],
                        (String) row[1],
                        ((UnitType) row[2]).name(),
                        ((Number) row[3]).longValue()))
                .toList();
    }

    private Instant periodStart(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "TODAY" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "WEEK"  -> today.with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
            default      -> today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }
}
