package com.delivera.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.delivera.order.model.OrderPriority;
import com.delivera.order.model.OrderType;

@Getter
@Setter
public class DataOrderRequest {
    @NotNull 
    private UUID originId;
    private UUID destinationId;
    @Email @Size(max = 255) 
    private String recipientEmail;
    @Size(max = 255) 
    private String recipientName;
    @Size(max = 500) 
    private String recipientAddress;
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") 
    private BigDecimal recipientLatitude;
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") 
    private BigDecimal recipientLongitude;
    @NotNull 
    private OrderType orderType;
    private OrderPriority priority;
    @Size(max = 1000) 
    private String notes;
    
    private UUID loyalUserId;
    private UUID currentCompanyId;

    // seed
    private Instant createdAt;
    private String reference;
    private Boolean claimed;

    @AssertTrue(message = "Latitude and longitude must both be provided or both be absent")
    public boolean isCoordinatesConsistent() {
        return (recipientLatitude == null) == (recipientLongitude == null);
    }

    @AssertTrue(message = "B2C orders require recipientEmail")
    public boolean isB2cEmailPresent() {
        return orderType != OrderType.B2C || (recipientEmail != null && !recipientEmail.isBlank());
    }

    @AssertTrue(message = "MISSING_RECIPIENT_LOCATION")
    public boolean isB2cLocationPresent() {
        if (orderType != OrderType.B2C) return true;
        boolean hasAddress = recipientAddress != null && !recipientAddress.isBlank();
        boolean hasCoords = recipientLatitude != null && recipientLongitude != null;
        return hasAddress || hasCoords;
    }
}
