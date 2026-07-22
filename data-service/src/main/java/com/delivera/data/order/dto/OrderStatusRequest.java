package com.delivera.data.order.dto;

import java.util.UUID;

import com.delivera.data.order.model.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class OrderStatusRequest {
        @NotNull 
        private OrderStatus status;
        @Size(max = 1000) 
        private String note; 
        
        private UUID companyId;

        private String email;
}

        
