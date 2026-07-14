package com.delivera.controller;

import com.delivera.dto.chat.OrderMessageRequest;
import com.delivera.dto.chat.OrderMessageResponse;
import com.delivera.order.controller.OrderMessageController;
import com.delivera.order.service.OrderMessageService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMessageControllerTest {

    @Mock private OrderMessageService orderMessageService;
    @InjectMocks private OrderMessageController controller;

    @Test
    void getMessages_returnsAll() {
        UUID orderId = UUID.randomUUID();
        OrderMessageResponse msg = new OrderMessageResponse(UUID.randomUUID(), null, "User", "hello", null);
        when(orderMessageService.getMessages(orderId)).thenReturn(List.of(msg));
        List<OrderMessageResponse> result = controller.getMessages(orderId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("hello");
    }

    @Test
    void sendMessage_delegatesAndReturns() {
        UUID orderId = UUID.randomUUID();
        OrderMessageRequest req = new OrderMessageRequest("texto del mensaje");
        OrderMessageResponse expected = new OrderMessageResponse(UUID.randomUUID(), null, "User", "texto del mensaje", null);
        when(orderMessageService.sendMessage(orderId, req)).thenReturn(expected);
        assertThat(controller.sendMessage(orderId, req)).isSameAs(expected);
    }
}
