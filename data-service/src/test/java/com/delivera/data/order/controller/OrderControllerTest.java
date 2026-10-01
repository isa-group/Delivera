package com.delivera.data.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import com.delivera.data.auth.service.AuthRateLimiter;
import com.delivera.data.auth.service.AuthService;
import com.delivera.data.order.dto.ClaimRegisterRequest;
import com.delivera.data.order.dto.LoginResponse;
import com.delivera.data.order.dto.OrderDetailResponse;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.dto.PublicOrderResponse;
import com.delivera.data.order.dto.RefreshCookieData;
import com.delivera.data.order.dto.RequestClientData;
import com.delivera.data.order.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith (MockitoExtension.class)
class OrderControllerTest {

    @Mock 
    private OrderService orderService;

    @Mock
    private AuthService authService;

    @Mock
    private AuthRateLimiter authRateLimiter;

    @InjectMocks 
    private OrderController controller;

    @Mock
    private HttpServletRequest httpRequest;

    private UUID orderId;

    @BeforeEach 
    void setup() {
        orderId = UUID.randomUUID();
    }
    @Test
    void list_shouldReturnOrders() {

        List<OrderResponse> orders = List.of();

        when(orderService.getByCompany())
                .thenReturn(orders);

        ResponseEntity<List<OrderResponse>> response =
                controller.list();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(orders);
    }
    @Test
    void getMyOrders_shouldReturnOrders() {
    
        List<OrderResponse> orders = List.of();
    
        when(orderService.getByEmail())
                .thenReturn(orders);
    
        ResponseEntity<List<OrderResponse>> response =
                controller.getMyOrders();
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .isSameAs(orders);
    }

    @Test
    void detail_shouldReturnOrder() {

        OrderDetailResponse result =
                mock(OrderDetailResponse.class);

        when(orderService.getDetail(orderId))
                .thenReturn(result);

        ResponseEntity<OrderDetailResponse> response =
                controller.detail(orderId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(result);
    }

    @Test
    void myOrderDetail_shouldReturnOrder() {

        PublicOrderResponse result =
                mock(PublicOrderResponse.class);

        when(orderService.getMyOrderDetail(orderId))
                .thenReturn(result);

        ResponseEntity<PublicOrderResponse> response =
                controller.myOrderDetail(orderId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(result);
    }

    @Test
    void create_shouldReturnCreated() {

        OrderRequest request =
                new OrderRequest();

        OrderResponse result =
                mock(OrderResponse.class);

        when(orderService.create(request))
                .thenReturn(result);

        ResponseEntity<OrderResponse> response =
                controller.create(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(result);
    }
    @Test
    void updateStatus_shouldReturnUpdatedOrder() {

        OrderStatusRequest request =
                new OrderStatusRequest();

        OrderDetailResponse result =
                mock(OrderDetailResponse.class);

        when(orderService.updateStatus(
                orderId,
                request
        )).thenReturn(result);

        ResponseEntity<OrderDetailResponse> response =
                controller.updateStatus(
                        orderId,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(result);
    }
    @Test
    void delete_shouldReturnNoContent() {

        ResponseEntity<Void> response =
                controller.delete(orderId);

        verify(orderService)
                .delete(orderId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
    @Test
    void trackByToken_shouldReturnOrder() {
    
        PublicOrderResponse result =
                mock(PublicOrderResponse.class);
    
        when(orderService.getPublicByToken("TOKEN"))
                .thenReturn(result);
    
        ResponseEntity<PublicOrderResponse> response =
                controller.trackByToken("TOKEN");
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .isSameAs(result);
    }

    @Test
    void trackByReference_shouldCheckRateLimit() {

        PublicOrderResponse result =
                mock(PublicOrderResponse.class);

        when(orderService.getPublicByReference("REF"))
                .thenReturn(result);

        ResponseEntity<PublicOrderResponse> response =
                controller.trackByReference(
                        httpRequest,
                        "REF"
                );

        verify(authRateLimiter)
                .check(
                       nullable(String.class),
                        eq("public-search")
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(result);
    }
    @Test
    void claimRegister_shouldReturnCreatedResponseWithCookie() {

        ClaimRegisterRequest request =
                mock(ClaimRegisterRequest.class);

        RefreshCookieData refreshCookieData =
                mock(RefreshCookieData.class);

        LoginResponse loginResponse =
                new LoginResponse("","",UUID.randomUUID(),
                "","","","",null, null);

        loginResponse.setRefreshCookie(
                refreshCookieData
        );

        ResponseCookie cookie =
                ResponseCookie.from(
                        "refreshToken",
                        "token"
                ).build();

        HttpServletRequest httpRequest = null;
        
        when(authService.getIp(httpRequest))
                .thenReturn("127.0.0.1");

        when(authService.getDeviceId(httpRequest))
                .thenReturn("device-1");

        when(authService.getUserAgent(httpRequest))
                .thenReturn("Mozilla");

        when(
                orderService.claimOrder(
                        eq(request),
                        any(RequestClientData.class),
                        eq("tracking-token")
                )
        ).thenReturn(loginResponse);

        when(
                authService.refreshCookie(
                        refreshCookieData
                )
        ).thenReturn(cookie);

        ResponseEntity<LoginResponse> response =
                controller.claimRegister(
                        httpRequest,
                        "tracking-token",
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.SET_COOKIE)
        ).isEqualTo(cookie.toString());

        assertThat(
                response.getBody()
        ).isSameAs(loginResponse);

        assertThat(
                response.getBody()
                        .getRefreshCookie()
        ).isNull();
    }
}