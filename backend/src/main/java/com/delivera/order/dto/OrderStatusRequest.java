package com.delivera.order.dto;

import com.delivera.order.model.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 1000) String note) {}
