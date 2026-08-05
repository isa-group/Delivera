package com.delivera.data.order.dto;



import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.order.model.Order;

public record OrderDetailResponse(
        UUID id,
        String reference,
        String orderType,
        UUID originId,
        String originName,
        UUID originCompanyId,
        //No lo tenemos String originCompanyName,
        UUID destinationId,
        String destinationName,
        UUID destinationCompanyId,
        // No lo tenemos String destinationCompanyName,
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
        UUID lu = order.getLoyalUserId();
        OperationalUnit dest = order.getDestination();
        OperationalUnit origin = order.getOrigin();
        UUID originCompanyId = origin.getCompanyId();
        UUID destCompanyId = dest != null ? dest.getCompanyId() : null;
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
                originCompanyId,
                // TODO:P002-Company NO LO TENEMOS: originCompany != null ? originCompany.getName() : null,
                dest != null ? dest.getId() : null,
                dest != null ? dest.getName() : null,
                destCompanyId,
                //TODO:P002-Company No LO TENEMOS: destCompany != null ? destCompany.getName() : null,
                order.getRecipientEmail(),
                order.getRecipientName(),
                order.getRecipientAddress(),
                order.getStatus().name(),
                order.getPriority().name(),
                order.getTrackingToken(),
               order.getClaimed(),
                order.getNotes(),
                lu,
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
