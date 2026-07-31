package com.delivera.data.admin.service;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.data.activity.dto.ActivityMetricsResponse;
import com.delivera.data.activity.dto.OrdersByDayEntry;
import com.delivera.data.common.dto.IdCountProjection;
import com.delivera.data.depot.dto.UnitAdminSummary;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.order.dto.OrderAdminSummary;
import com.delivera.data.order.dto.RouteAdminEntry;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.repository.OrderEventRepository;
import com.delivera.data.order.repository.OrderMessageRepository;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.org.repository.SettingsRepository;
import com.delivera.data.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final OperationalUnitRepository unitRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderMessageRepository orderMessageRepository;
    private final WorkerRepository workerRepository;
    private final SettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public Map<UUID,Long> countByOrganization() {
        return orderRepository.countByOrganization()
        .stream()
        .collect(Collectors.toMap(
            IdCountProjection::getId, IdCountProjection::getCount
        ));
    }

    
    @Transactional(readOnly = true)
    public Map<UUID,Long> countByCompany() {
        return orderRepository.countByCompany()
        .stream()
        .collect(Collectors.toMap(
            IdCountProjection::getId, IdCountProjection::getCount
        ));
    }

    @Transactional(readOnly = true)
    public List<OrderAdminSummary> getOrdersSummary() {
        return orderRepository.findSummaryOfAll();
    }


    @Transactional(readOnly = true)
    public ActivityMetricsResponse getGlobalActivityMetrics(String period) {
        Instant from = periodStart(period);
        return new ActivityMetricsResponse(
                period,
                orderRepository.countByCreatedAtAfter(from),
                orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.DELIVERED, from),
                orderRepository.countByStatusAndCreatedAtAfter(OrderStatus.CANCELLED, from),
                orderRepository.countByStatusInAndCreatedAtAfter(List.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT), from)
        );
    }

    @Transactional(readOnly = true)
    public List<OrdersByDayEntry> getGlobalOrdersByDay(String period) {
        Instant from = periodStart(period);
        return orderRepository.countByDayGlobal(from).stream()
                .map(row -> {
                    LocalDate date = row[0] instanceof LocalDate d ? d : ((Date) row[0]).toLocalDate();
                    return new OrdersByDayEntry(date, ((Number) row[1]).longValue());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Long getOrdersThisMonth() {
        Instant monthStart = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return orderRepository.countByCreatedAtAfter(monthStart);
                
    }

     @Transactional(readOnly = true)
    public List<UnitAdminSummary> listUnits() {
        return unitRepository.findAdminSummaries();
    }

    
    @Transactional(readOnly = true)
    public List<IdCountProjection> getCompanyRanking(String period) {
        Instant from = periodStart(period);
        return orderRepository.rankCompaniesByOrderCount(from);
    }

    @Transactional(readOnly = true)
    public List<RouteAdminEntry> getActiveRoutes() {
        return orderRepository.findAllActiveWithPositions(Set.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT));
    }

    @Transactional
    public void deleteAllDataExcept(UUID companyId) {
        vehicleRepository.deleteAllExceptCompanyId(companyId);
        orderEventRepository.deleteAllExceptCompanyIds(companyId);
        orderMessageRepository.deleteAllExceptCompanyId(companyId);
        orderRepository.deleteAllExceptCompanyId(companyId);
        workerRepository.deleteAllExceptCompanyId(companyId);
        unitRepository.deleteAllExceptCompanyId(companyId);
        settingsRepository.deleteAllExceptCompanyId(companyId);

    }


    private static Instant periodStart(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "TODAY" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "WEEK"  -> today.with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
            default      -> today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }
}
