package com.delivera.data.order.controller;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.exception.ForbiddenException;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.model.OrderType;
import com.delivera.data.order.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/orders")
public class OrderInternalController {

    private final OrderService orderService;

    @PostMapping("/B2C")
    public ResponseEntity<OrderResponse> createB2C(@Valid @RequestBody OrderRequest orderRequest) {
        if (!orderRequest.getOrderType().equals(OrderType.B2C)) {
            throw new ForbiddenException("THE ORDER MUST BE B2C");
        }
        return ResponseEntity.status(201).body(
            orderService.createB2C(orderRequest)
        );

    }

    @PostMapping("/seed")
    public ResponseEntity<UUID> createSeed(@Valid @RequestBody OrderRequest orderRequest) {
        OrderResponse response =  orderService.create(orderRequest, orderRequest.getCurrentCompanyId(), null);
        return ResponseEntity.status(201).body(response.id());

    }

    @PostMapping("/{orderId}/seed/events")
    public ResponseEntity<Void> createSeedEvents(
        @Valid @PathVariable(name = "orderId") UUID orderId,
        @Valid @RequestBody OrderStatusRequest orderRequest
    ) {
        orderService.updateStatusSeed(orderId,orderRequest);
        return ResponseEntity.status(201).build();

    }

    /* TODO
    @PostMapping("/public/track/{token}/register/{LoyalUserId}")
    public ResponseEntity<String> claimOrder(
        @Valid @PathVariable(name = "token") String  token,
        @Valid @PathVariable(name = "LoyalUserId") UUID loyalUserId,
        @Valid @RequestBody String email
    ) {
       
        return ResponseEntity.status(200).body(
            orderService.claimOrder(token,email ,loyalUserId)
        );

    }*/

}
