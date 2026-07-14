package com.delivera.order.dto;

import com.delivera.depot.model.OperationalUnit;
import com.delivera.model.LoyalUser;
import com.delivera.order.model.Order;
import com.delivera.org.model.Company;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID id,
        String reference,
        String orderType,
        UUID originId,
        String originName,
        UUID originCompanyId,
        String originCompanyName,
        UUID destinationId,
        String destinationName,
        UUID destinationCompanyId,
        String destinationCompanyName,
        String recipientEmail,
        String recipientName,
        String recipientAddress,
        String status,
        String priority,
        String trackingToken,
        boolean claimed,
        String notes,
        UUID loyalUserId,
        Instant createdAt,
        List<OrderEventResponse> events,
        Double originLat,
        Double originLon,
        Double destinationLat,
        Double destinationLon,
        Double currentLat,
        Double currentLon,
        Instant currentLocationAt) {

    public static OrderDetailResponse from(Order order) {
        LoyalUser lu = order.getLoyalUser();
        OperationalUnit dest = order.getDestination();
        OperationalUnit origin = order.getOrigin();
        Company originCompany = origin.getCompany();
        Company destCompany = dest != null ? dest.getCompany() : null;
        Double destLat = resolveDestCoord(dest != null ? dest.getLatitude() : null, order.getRecipientLatitude());
        Double destLon = resolveDestCoord(dest != null ? dest.getLongitude() : null, order.getRecipientLongitude());
        List<OrderEventResponse> events = order.getEvents() != null
                ? order.getEvents().stream().map(OrderEventResponse::from).toList()
                : List.of();
        return new OrderDetailResponse(
                order.getId(),
                order.getReference(),
                order.getOrderType().name(),
                origin.getId(),
                origin.getName(),
                originCompany != null ? originCompany.getId() : null,
                originCompany != null ? originCompany.getName() : null,
                dest != null ? dest.getId() : null,
                dest != null ? dest.getName() : null,
                destCompany != null ? destCompany.getId() : null,
                destCompany != null ? destCompany.getName() : null,
                order.getRecipientEmail(),
                order.getRecipientName(),
                order.getRecipientAddress(),
                order.getStatus().name(),
                order.getPriority().name(),
                order.getTrackingToken(),
                lu != null && lu.getUser() != null,
                order.getNotes(),
                lu != null ? lu.getId() : null,
                order.getCreatedAt(),
                events,
                toDouble(origin.getLatitude()),
                toDouble(origin.getLongitude()),
                destLat,
                destLon,
                toDouble(order.getCurrentLat()),
                toDouble(order.getCurrentLon()),
                order.getCurrentLocationAt());
    }

    private static Double resolveDestCoord(BigDecimal destCoord, BigDecimal recipientCoord) {
        if (destCoord != null) return destCoord.doubleValue();
        return recipientCoord != null ? recipientCoord.doubleValue() : null;
    }

    private static Double toDouble(BigDecimal v) {
        return v != null ? v.doubleValue() : null;
    }
}
