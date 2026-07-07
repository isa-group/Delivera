package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.order.model.OrderEvent;

import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {
}
