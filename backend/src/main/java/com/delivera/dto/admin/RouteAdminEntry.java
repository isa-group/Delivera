package com.delivera.dto.admin;

import java.util.UUID;

public record RouteAdminEntry(
        UUID id,
        String reference,
        String status,
        double originLat,
        double originLon,
        UUID originId,
        String originName,
        double destinationLat,
        double destinationLon,
        UUID destinationId,
        String destinationName
) {}
