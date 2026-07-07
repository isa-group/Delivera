package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.order.model.OrderMessage;

import java.util.List;
import java.util.UUID;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, UUID> {
    List<OrderMessage> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
