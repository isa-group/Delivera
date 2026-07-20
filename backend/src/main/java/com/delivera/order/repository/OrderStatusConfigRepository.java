package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.order.model.OrderStatusConfig;

import java.util.List;

public interface OrderStatusConfigRepository extends JpaRepository<OrderStatusConfig, String> {
    List<OrderStatusConfig> findAllByOrderBySortOrderAsc();
}
