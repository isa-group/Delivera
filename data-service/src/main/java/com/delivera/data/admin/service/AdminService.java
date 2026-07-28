package com.delivera.data.admin.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.data.common.dto.IdCountProjection;
import com.delivera.data.order.dto.OrderAdminSummary;
import com.delivera.data.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;

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
}
