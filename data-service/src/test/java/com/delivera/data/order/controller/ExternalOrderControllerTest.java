package com.delivera.data.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.data.order.dto.OrderDetailResponse;
import com.delivera.data.order.dto.OrderLocationRequest;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.service.OrderService;

@ExtendWith (MockitoExtension.class)
class ExternalOrderControllerTest {

    @Mock 
    private OrderService orderService;

    @InjectMocks 
    private ExternalOrderController controller;

    private UUID orderId;

    @BeforeEach
    void setup() {
        orderId = UUID.randomUUID();
    }

    @Test
    void create_shouldReturnCreatedOrder() {

        OrderRequest request =
                new OrderRequest();

        OrderResponse response =
                mock(OrderResponse.class);

        when(orderService.create(request))
                .thenReturn(response);

        ResponseEntity<OrderResponse> result =
                controller.create(request);

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(result.getBody())
                .isSameAs(response);

        verify(orderService)
                .create(request);
    }
    @Test
    void updateStatus_shouldReturnUpdatedOrder() {

        OrderStatusRequest request =
                new OrderStatusRequest();

        OrderDetailResponse response =
                mock(OrderDetailResponse.class);

        when(
                orderService.updateStatus(
                        orderId,
                        request
                )
        ).thenReturn(response);

        ResponseEntity<OrderDetailResponse> result =
                controller.updateStatus(
                        orderId,
                        request
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
                .isSameAs(response);

        verify(orderService)
                .updateStatus(
                        orderId,
                        request
                );
    }
    @Test
    void updateLocation_shouldReturnUpdatedLocation() {

        OrderLocationRequest request =
                new OrderLocationRequest(
                        BigDecimal.valueOf(40.123),
                        BigDecimal.valueOf(-3.456)
                );

        OrderDetailResponse response =
                mock(OrderDetailResponse.class);

        when(
                orderService.updateLocation(
                        orderId,
                        request
                )
        ).thenReturn(response);

        ResponseEntity<OrderDetailResponse> result =
                controller.updateLocation(
                        orderId,
                        request
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
                .isSameAs(response);

        verify(orderService)
                .updateLocation(
                        orderId,
                        request
                );
    }
}
