package com.delivera.controller;

import com.delivera.auth.service.AuthService;
import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.order.*;
import com.delivera.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Gestión de pedidos")
public class OrderController {

    private final OrderService orderService;
    private final AuthService authService;

    @Operation(summary = "Listar pedidos de la empresa")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> list() {
        return ResponseEntity.ok(orderService.getByCompany());
    }

    @Operation(summary = "Detalle de un pedido")
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getDetail(id));
    }

    @Operation(summary = "Crear pedido")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @Operation(summary = "Actualizar estado del pedido")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDetailResponse> updateStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request));
    }

    @Operation(summary = "Eliminar pedido")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Seguimiento público por token")
    @GetMapping("/public/track/{token}")
    public ResponseEntity<PublicOrderResponse> trackByToken(@PathVariable String token) {
        return ResponseEntity.ok(orderService.getPublicByToken(token));
    }

    @Operation(summary = "Seguimiento público por referencia")
    @GetMapping("/public/search")
    public ResponseEntity<PublicOrderResponse> trackByReference(@RequestParam String reference) {
        return ResponseEntity.ok(orderService.getPublicByReference(reference));
    }

    @Operation(summary = "Registro de destinatario a través del token de seguimiento")
    @PostMapping("/public/track/{token}/register")
    public ResponseEntity<LoginResponse> claimRegister(@PathVariable String token,
                                                       @Valid @RequestBody ClaimRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.claimRegister(token, request));
    }
}
