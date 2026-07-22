package com.delivera.data.order.controller;



import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.delivera.data.order.dto.OrderDetailResponse;
import com.delivera.data.order.dto.OrderLocationRequest;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.service.OrderService;

@RestController
@RequestMapping("/external/orders")
@RequiredArgsConstructor
@Tag(name = "API externa - Pedidos", description = "Creación de pedidos desde sistemas externos mediante API key")
public class ExternalOrderController {

    private final OrderService orderService;

    @Operation(summary = "Crear pedido desde sistema externo")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @Operation(summary = "Actualizar estado de pedido desde sistema externo")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDetailResponse> updateStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request));
    }

    @Operation(summary = "Reportar última posición conocida del pedido desde sistema externo")
    @PatchMapping("/{id}/location")
    public ResponseEntity<OrderDetailResponse> updateLocation(@PathVariable UUID id,
                                                              @Valid @RequestBody OrderLocationRequest request) {
        return ResponseEntity.ok(orderService.updateLocation(id, request));
    }
}
