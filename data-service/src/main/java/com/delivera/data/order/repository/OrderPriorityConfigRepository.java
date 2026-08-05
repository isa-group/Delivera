package com.delivera.data.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.data.order.model.OrderPriorityConfig;

import java.util.List;

public interface OrderPriorityConfigRepository extends JpaRepository<OrderPriorityConfig, String> {
    List<OrderPriorityConfig> findAllByOrderBySortOrderAsc();
}
