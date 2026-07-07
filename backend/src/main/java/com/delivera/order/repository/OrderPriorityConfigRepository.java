package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.order.model.OrderPriorityConfig;

import java.util.List;

public interface OrderPriorityConfigRepository extends JpaRepository<OrderPriorityConfig, String> {
    List<OrderPriorityConfig> findAllByOrderBySortOrderAsc();
}
