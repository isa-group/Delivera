package com.delivera.dto.order;

import com.delivera.depot.model.OperationalUnit;
import com.delivera.model.LoyalUser;
import com.delivera.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String reference,
        String orderType,
        UUID originId,
        String originName,
        UUID originCompanyId,
        UUID destinationId,
        String destinationName,
        UUID destinationCompanyId,
        String recipientEmail,
        String recipientName,
        String recipientAddress,
        String status,
        String priority,
        String notes,
        String trackingToken,
        boolean claimed,
        UUID loyalUserId,
        Instant createdAt,
        Double originLat,
        Double originLon,
        Double destinationLat,
        Double destinationLon,
        Double currentLat,
        Double currentLon,
        Instant currentLocationAt) {

    public static OrderResponse from(Order order) {
        LoyalUser lu = order.getLoyalUser();
        OperationalUnit dest = order.getDestination();
        OperationalUnit origin = order.getOrigin();
        boolean claimed = lu != null && lu.getUser() != null;
        Double destLat = resolveDestCoord(dest != null ? dest.getLatitude() : null, order.getRecipientLatitude());
        Double destLon = resolveDestCoord(dest != null ? dest.getLongitude() : null, order.getRecipientLongitude());
        return new OrderResponse(
                order.getId(),
                order.getReference(),
                order.getOrderType().name(),
                origin.getId(),
                origin.getName(),
                origin.getCompany() != null ? origin.getCompany().getId() : null,
                dest != null ? dest.getId() : null,
                dest != null ? dest.getName() : null,
                dest != null && dest.getCompany() != null ? dest.getCompany().getId() : null,
                order.getRecipientEmail(),
                order.getRecipientName(),
                order.getRecipientAddress(),
                order.getStatus().name(),
                order.getPriority().name(),
                order.getNotes(),
                order.getTrackingToken(),
                claimed,
                lu != null ? lu.getId() : null,
                order.getCreatedAt(),
                origin.getLatitude() != null ? origin.getLatitude().doubleValue() : null,
                origin.getLongitude() != null ? origin.getLongitude().doubleValue() : null,
                destLat,
                destLon,
                order.getCurrentLat() != null ? order.getCurrentLat().doubleValue() : null,
                order.getCurrentLon() != null ? order.getCurrentLon().doubleValue() : null,
                order.getCurrentLocationAt());
    }

    private static Double resolveDestCoord(BigDecimal destCoord, BigDecimal recipientCoord) {
        if (destCoord != null) return destCoord.doubleValue();
        return recipientCoord != null ? recipientCoord.doubleValue() : null;
    }
}
