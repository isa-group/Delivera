package com.delivera.data.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.delivera.data.order.model.OrderStatus;


public record RouteAdminEntry(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal originLat,
        BigDecimal originLon,
        UUID originId,
        String originName,
        BigDecimal destinationLat,
        BigDecimal destinationLon,
        UUID destinationId,
        String destinationName
) {}
