package com.delivera.data.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.delivera.data.order.dto.OrderMessageRequest;
import com.delivera.data.order.dto.OrderMessageResponse;
import com.delivera.data.order.service.OrderMessageService;

@ExtendWith (MockitoExtension.class)
class OrderMessageControllerTest {

    @Mock 
    private OrderMessageService service;

    @InjectMocks 
    private OrderMessageController controller;

    private UUID orderId;

    @BeforeEach 
    void setup() {
        orderId = UUID.randomUUID();
    }
    @Test
    void getMessages_shouldReturnMessages() {

        List<OrderMessageResponse> messages =
                List.of(
                        mock(OrderMessageResponse.class),
                        mock(OrderMessageResponse.class)
                );

        when(
                service.getMessages(orderId)
        ).thenReturn(messages);

        List<OrderMessageResponse> response =
                controller.getMessages(orderId);

        assertThat(response)
                .isSameAs(messages);

        verify(service)
                .getMessages(orderId);
    }
    @Test
    void sendMessage_shouldReturnCreatedMessage() {

        OrderMessageRequest request =
                new OrderMessageRequest(
                        "Hello"
                );

        OrderMessageResponse response =
                mock(OrderMessageResponse.class);

        when(
                service.sendMessage(
                        orderId,
                        request
                )
        ).thenReturn(response);

        OrderMessageResponse result =
                controller.sendMessage(
                        orderId,
                        request
                );

        assertThat(result)
                .isSameAs(response);

        verify(service)
                .sendMessage(
                        orderId,
                        request
                );
    }
}