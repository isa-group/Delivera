package com.delivera.data.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.data.order.model.OrderStatusConfig;

import java.util.List;

public interface OrderStatusConfigRepository extends JpaRepository<OrderStatusConfig, String> {
    List<OrderStatusConfig> findAllByOrderBySortOrderAsc();
}
