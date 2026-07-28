package com.delivera.data.order.dto;

import java.time.Instant;
import java.util.UUID;

import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.model.OrderType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrderAdminSummary {
        private UUID id;
        private String reference;
        private OrderStatus status;
        private OrderType orderType;
        private UUID companyId;
        private Instant createdAt;
}
