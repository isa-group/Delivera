package com.delivera.order.controller;

import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.service.AuthService;
import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.security.AuthRateLimiter;
import com.delivera.order.dto.OrderDetailResponse;
import com.delivera.order.dto.OrderRequest;
import com.delivera.order.dto.OrderResponse;
import com.delivera.order.dto.OrderStatusRequest;
import com.delivera.order.dto.PublicOrderResponse;
import com.delivera.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private final AuthRateLimiter authRateLimiter;

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
    @PostMapping("/B2C")
    public ResponseEntity<OrderResponse> createB2C(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createB2C(request));
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
    public ResponseEntity<PublicOrderResponse> trackByReference(HttpServletRequest httpRequest,
                                                                @RequestParam String reference) {
        authRateLimiter.check(AuthRateLimiter.clientIp(httpRequest), "public-search");
        return ResponseEntity.ok(orderService.getPublicByReference(reference));
    }

    @Operation(summary = "Registro de destinatario a través del token de seguimiento")
    @PostMapping("/public/track/{token}/register")
    public ResponseEntity<LoginResponse> claimRegister(
        HttpServletRequest httpRequest,
        @PathVariable String token,
        @Valid @RequestBody ClaimRegisterRequest request
    ) {
        // TODO: P001-LoyalUser
        /* 
        String ip = authService.getIp(httpRequest);
        String deviceId = authService.getDeviceId(httpRequest);
        String userAgent = authService.getUserAgent(httpRequest);
        RequestClientData requestClientData = new RequestClientData(ip, deviceId, userAgent);

        LoginResponse response =  authService.claimRegister(token, request,requestClientData);
        RefreshCookieData refreshCookieData = response.getRefreshCookie();
        response.setRefreshCookie(null);
        ResponseCookie refreshCookie = authService.refreshCookie(refreshCookieData);
*/
        return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(null);

    }
}
