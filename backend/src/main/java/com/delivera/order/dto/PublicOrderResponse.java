package com.delivera.order.dto;

import com.delivera.depot.model.OperationalUnit;
import com.delivera.order.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicOrderResponse(
        UUID id,
        String reference,
        String companyName,
        String originName,
        String destinationName,
        String recipientName,
        String recipientAddress,
        String status,
        String priority,
        Instant createdAt,
        List<OrderEventResponse> events,
        boolean claimable,
        String recipientEmailHint,
        Double originLat,
        Double originLon,
        Double destinationLat,
        Double destinationLon) {

    public static PublicOrderResponse from(Order order) {
        boolean claimable = order.getTrackingToken() != null
                && (order.getLoyalUser() == null || order.getLoyalUser().getUser() == null);
        String hint = claimable && order.getRecipientEmail() != null
                ? maskEmail(order.getRecipientEmail()) : null;

        OperationalUnit dest = order.getDestination();
        Double destLat = resolveDestCoord(dest != null ? dest.getLatitude() : null, order.getRecipientLatitude());
        Double destLon = resolveDestCoord(dest != null ? dest.getLongitude() : null, order.getRecipientLongitude());

        return new PublicOrderResponse(
                order.getId(),
                order.getReference(),
                order.getCompany().getName(),
                order.getOrigin().getName(),
                dest != null ? dest.getName() : null,
                order.getRecipientName(),
                order.getRecipientAddress(),
                order.getStatus().name(),
                order.getPriority().name(),
                order.getCreatedAt(),
                order.getEvents().stream().map(OrderEventResponse::from).toList(),
                claimable,
                hint,
                order.getOrigin().getLatitude() != null ? order.getOrigin().getLatitude().doubleValue() : null,
                order.getOrigin().getLongitude() != null ? order.getOrigin().getLongitude().doubleValue() : null,
                destLat,
                destLon);
    }

    private static Double resolveDestCoord(BigDecimal destCoord, BigDecimal recipientCoord) {
        if (destCoord != null) return destCoord.doubleValue();
        return recipientCoord != null ? recipientCoord.doubleValue() : null;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
