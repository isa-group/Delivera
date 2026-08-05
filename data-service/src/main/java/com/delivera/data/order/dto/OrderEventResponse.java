package com.delivera.data.order.dto;

import java.time.Instant;
import java.util.UUID;

import com.delivera.data.order.model.OrderEvent;


public record OrderEventResponse(
        UUID id,
        String status,
        String note,
        String authorEmail,
        Instant createdAt) {

    public static OrderEventResponse from(OrderEvent e) {
        return new OrderEventResponse(e.getId(), e.getStatus().name(), e.getNote(), e.getAuthorEmail(), e.getCreatedAt());
    }
}
