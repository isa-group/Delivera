package com.delivera.data.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.data.exception.ForbiddenException;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.model.OrderType;
import com.delivera.data.order.service.OrderService;

@ExtendWith (MockitoExtension.class)
class OrderInternalControllerTest {

    @Mock 
    private OrderService orderService;

    @InjectMocks 
    private OrderInternalController controller;

    private UUID orderId;
    private UUID companyId;

    @BeforeEach 
    void setup() {

        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }
    @Test
    void createB2C_shouldCreateOrder() {

        OrderRequest request =
                new OrderRequest();

        request.setOrderType(
                OrderType.B2C
        );

        OrderResponse response =
                mock(OrderResponse.class);

        when(
                orderService.createB2C(request)
        ).thenReturn(response);

        ResponseEntity<OrderResponse> result =
                controller.createB2C(request);

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(result.getBody())
                .isSameAs(response);

        verify(orderService)
                .createB2C(request);
    }
    @Test
    void createB2C_shouldThrowForbiddenException() {

        OrderRequest request =
                new OrderRequest();

        request.setOrderType(
                OrderType.INTERNAL
        );

        assertThatThrownBy(
                () -> controller.createB2C(request)
        ).isInstanceOf(
                ForbiddenException.class
        );

        verify(orderService, never())
                .createB2C(any());
    }
    @Test
    void createSeed_shouldReturnCreatedAndOrderId() {
    
        UUID createdOrderId =
                UUID.randomUUID();
    
        OrderRequest request =
                new OrderRequest();
    
        request.setCurrentCompanyId(companyId);
    
        OrderResponse response =
                mock(OrderResponse.class);
    
        when(response.id())
                .thenReturn(createdOrderId);
    
        when(
                orderService.create(
                        request,
                        companyId,
                        null
                )
        ).thenReturn(response);
    
        ResponseEntity<UUID> result =
                controller.createSeed(request);
    
        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    
        assertThat(result.getBody())
                .isEqualTo(createdOrderId);
    
        verify(orderService)
                .create(
                        request,
                        companyId,
                        null
                );
    }
    @Test
    void createSeedEvents_shouldReturnCreated() {

        OrderStatusRequest request =
                new OrderStatusRequest();

        ResponseEntity<Void> result =
                controller.createSeedEvents(
                        orderId,
                        request
                );

        verify(orderService)
                .updateStatusSeed(
                        orderId,
                        request
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

}