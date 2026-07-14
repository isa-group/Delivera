package com.delivera.controller;

import com.delivera.dto.loyaluser.LoyalUserRequest;
import com.delivera.dto.loyaluser.LoyalUserResponse;
import com.delivera.order.dto.OrderResponse;
import com.delivera.service.LoyalUserService;
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
@RequestMapping("/loyal-users")
@Tag(name = "Fidelizados", description = "Gestión de usuarios fidelizados")
public class LoyalUserController {

    private final LoyalUserService loyalUserService;

    @Operation(summary = "Listar fidelizados de la empresa")
    @GetMapping
    public ResponseEntity<List<LoyalUserResponse>> list() {
        return ResponseEntity.ok(loyalUserService.getByCompany());
    }

    @Operation(summary = "Añadir fidelizado por email")
    @PostMapping
    public ResponseEntity<LoyalUserResponse> add(@Valid @RequestBody LoyalUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loyalUserService.add(request));
    }

    @Operation(summary = "Actualizar dirección del fidelizado")
    @PutMapping("/{id}/address")
    public ResponseEntity<LoyalUserResponse> updateAddress(@PathVariable UUID id,
                                                           @Valid @RequestBody LoyalUserRequest request) {
        return ResponseEntity.ok(loyalUserService.updateAddress(id, request));
    }

    @Operation(summary = "Pedidos de un fidelizado")
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderResponse>> orders(@PathVariable UUID id) {
        return ResponseEntity.ok(loyalUserService.getOrdersForLoyalUser(id));
    }

    @Operation(summary = "Mis pedidos (usuario fidelizado autenticado)")
    @GetMapping("/me/orders")
    public ResponseEntity<List<OrderResponse>> myOrders() {
        return ResponseEntity.ok(loyalUserService.getMyOrders());
    }
}
