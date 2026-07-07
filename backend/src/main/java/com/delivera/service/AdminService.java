package com.delivera.service;

import com.delivera.dto.admin.GlobalMetrics;
import com.delivera.dto.admin.OrganizationSummary;
import com.delivera.order.repository.OrderRepository;
import com.delivera.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final OrganizationRepository organizationRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AdminService(OrganizationRepository organizationRepository,
                        CompanyRepository companyRepository,
                        OrderRepository orderRepository,
                        UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.companyRepository = companyRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationSummary> listOrganizations() {
        return organizationRepository.findAllSummaries().stream()
                .map(row -> new OrganizationSummary(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Instant) row[3],
                        (Long) row[4],
                        (Long) row[5],
                        (Long) row[6]))
                .toList();
    }

    @Transactional(readOnly = true)
    public GlobalMetrics getGlobalMetrics() {
        Instant monthStart = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return new GlobalMetrics(
                organizationRepository.count(),
                companyRepository.count(),
                orderRepository.countByCreatedAtAfter(monthStart),
                userRepository.count());
    }
}
