package com.delivera.data.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.exception.OrderNotFoundException;
import com.delivera.data.order.dto.OrderMessageRequest;
import com.delivera.data.order.dto.OrderMessageResponse;
import com.delivera.data.order.model.Order;
import com.delivera.data.order.model.OrderMessage;
import com.delivera.data.order.repository.OrderMessageRepository;
import com.delivera.data.order.repository.OrderRepository;

@ExtendWith (MockitoExtension.class)
class OrderMessageServiceTest {

    @Mock
    private OrderMessageRepository messageRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock 
    private SecurityUtils securityUtils;

    @InjectMocks 
    private OrderMessageService service;

    private UUID orderId;
    private UUID companyId;
    private UUID userId;
    private String email;

    @BeforeEach 
    void setup() {

        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        email = "test@test.com";
    }
    @Test
    void getMessages_shouldReturnMessagesForCompanyUser() {

        Order order = new Order();

        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                orderRepository.findByIdForCompany(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.of(order));

        when(
                messageRepository.findByOrderIdOrderByCreatedAtAsc(
                        orderId
                )
        ).thenReturn(List.of());

        List<OrderMessageResponse> result =
                service.getMessages(orderId);

        assertThat(result).isEmpty();

        verify(messageRepository)
                .findByOrderIdOrderByCreatedAtAsc(orderId);
    }
    @Test
    void getMessages_shouldUseLoyalUserLookup() {
    
        Order order = new Order();
    
        when(securityUtils.getCurrentRole())
                .thenReturn("LOYAL_USER");
    
        when(securityUtils.getCurrentEmail())
                .thenReturn(email);
    
        when(
                orderRepository.findByIdForLoyalUser(
                        orderId,
                        email
                )
        ).thenReturn(Optional.of(order));
    
        when(
                messageRepository.findByOrderIdOrderByCreatedAtAsc(
                        orderId
                )
        ).thenReturn(List.of());
    
        service.getMessages(orderId);
    
        verify(orderRepository)
                .findByIdForLoyalUser(
                        orderId,
                        email
                );
    }
    @Test
    void getMessages_shouldThrowWhenOrderNotFound() {

        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                orderRepository.findByIdForCompany(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getMessages(orderId)
        ).isInstanceOf(
                OrderNotFoundException.class
        );
    }
    @Test
    void sendMessage_shouldCreateMessage() {

        Order order = new Order();

        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentEmail())
                .thenReturn(email);

        when(securityUtils.getCurrentUserId())
                .thenReturn(userId);

        when(
                orderRepository.findByIdForCompany(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.of(order));

        when(
                messageRepository.save(any(OrderMessage.class))
        ).thenAnswer(i -> i.getArgument(0));

        OrderMessageRequest request =
                new OrderMessageRequest("hello");

        OrderMessageResponse result =
                service.sendMessage(
                        orderId,
                        request
                );

        assertThat(result).isNotNull();

        verify(messageRepository)
                .save(any(OrderMessage.class));
    }
    @Test
    void sendMessage_shouldResolveOrderForLoyalUser() {
    
        Order order = new Order();
    
        when(securityUtils.getCurrentRole())
                .thenReturn("LOYAL_USER");
    
        when(securityUtils.getCurrentEmail())
                .thenReturn(email);
    
        when(securityUtils.getCurrentUserId())
                .thenReturn(userId);
    
        when(
                orderRepository.findByIdForLoyalUser(
                        orderId,
                        email
                )
        ).thenReturn(Optional.of(order));
    
        when(
                messageRepository.save(any(OrderMessage.class))
        ).thenAnswer(i -> i.getArgument(0));
    
        service.sendMessage(
                orderId,
                new OrderMessageRequest("test")
        );
    
        verify(orderRepository)
                .findByIdForLoyalUser(
                        orderId,
                        email
                );
    }
    @Test
    void sendMessage_shouldThrowWhenOrderNotFound() {
    
        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
                orderRepository.findByIdForCompany(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
                () -> service.sendMessage(
                        orderId,
                        new OrderMessageRequest("test")
                )
        ).isInstanceOf(
                OrderNotFoundException.class
        );
    }
    @Test
    void sendMessage_shouldPopulateMessageFields() {

        Order order = new Order();

        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentEmail())
                .thenReturn(email);

        when(securityUtils.getCurrentUserId())
                .thenReturn(userId);

        when(
                orderRepository.findByIdForCompany(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.of(order));

        when(
                messageRepository.save(any(OrderMessage.class))
        ).thenAnswer(i -> i.getArgument(0));

        OrderMessageRequest request =
                new OrderMessageRequest("Contenido");

        service.sendMessage(
                orderId,
                request
        );

        ArgumentCaptor<OrderMessage> captor =
                ArgumentCaptor.forClass(OrderMessage.class);

        verify(messageRepository)
                .save(captor.capture());

        assertThat(captor.getValue().getSender())
                .isEqualTo(userId);

        assertThat(captor.getValue().getContent())
                .isEqualTo("Contenido");

        assertThat(captor.getValue().getOrder())
                .isSameAs(order);
    }


}