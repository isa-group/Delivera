package com.delivera.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.chat.OrderMessageRequest;
import com.delivera.dto.chat.OrderMessageResponse;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.OrderNotFoundException;
import com.delivera.model.*;
import com.delivera.repository.OrderMessageRepository;
import com.delivera.repository.OrderRepository;
import com.delivera.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderMessageServiceTest {

    @Mock private OrderMessageRepository messageRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private SecurityUtils securityUtils;
    @InjectMocks private OrderMessageService service;

    private UUID orderId;
    private UUID companyId;
    private Order order;
    private User sender;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        var company = new Company();
        company.setId(companyId);

        order = new Order();
        order.setId(orderId);
        order.setCompany(company);

        sender = new User();
        sender.setId(UUID.randomUUID());
        sender.setEmail("worker@test.com");
        sender.setFirstName("Ana");
        sender.setLastName("López");
    }

    @Test
    void getMessages_returnsListForValidCompany() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.of(order));

        var msg = new OrderMessage();
        msg.setId(UUID.randomUUID());
        msg.setOrder(order);
        msg.setSender(sender);
        msg.setContent("Hola");
        msg.setCreatedAt(Instant.now());
        when(messageRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(msg));

        List<OrderMessageResponse> result = service.getMessages(orderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("Hola");
        assertThat(result.get(0).senderName()).isEqualTo("Ana López");
    }

    @Test
    void getMessages_throwsOrderNotFound_whenOrderDoesNotBelongToCompany() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getMessages_returnsEmptyList_whenNoMessages() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.of(order));
        when(messageRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        assertThat(service.getMessages(orderId)).isEmpty();
    }

    @Test
    void sendMessage_persistsAndReturnsMessage() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.of(order));
        when(securityUtils.getCurrentEmail()).thenReturn("worker@test.com");
        when(userRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(sender));

        var saved = new OrderMessage();
        saved.setId(UUID.randomUUID());
        saved.setOrder(order);
        saved.setSender(sender);
        saved.setContent("Mensaje de prueba");
        saved.setCreatedAt(Instant.now());
        when(messageRepository.save(any(OrderMessage.class))).thenReturn(saved);

        OrderMessageResponse result = service.sendMessage(orderId, new OrderMessageRequest("Mensaje de prueba"));

        assertThat(result.content()).isEqualTo("Mensaje de prueba");
        assertThat(result.senderId()).isEqualTo(sender.getId());
        verify(messageRepository).save(any(OrderMessage.class));
    }

    @Test
    void sendMessage_throwsOrderNotFound_whenOrderDoesNotBelongToCompany() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(orderId, new OrderMessageRequest("Hola")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getMessages_loyalUser_canAccessOwnOrder() {
        String email = "loyal@test.com";
        when(securityUtils.getCurrentRole()).thenReturn("LOYAL_USER");
        when(securityUtils.getCurrentEmail()).thenReturn(email);
        when(orderRepository.findByIdForLoyalUser(orderId, email)).thenReturn(Optional.of(order));
        when(messageRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        assertThat(service.getMessages(orderId)).isEmpty();
    }

    @Test
    void getMessages_loyalUser_throwsOrderNotFound_whenOrderNotLinked() {
        String email = "other@test.com";
        when(securityUtils.getCurrentRole()).thenReturn("LOYAL_USER");
        when(securityUtils.getCurrentEmail()).thenReturn(email);
        when(orderRepository.findByIdForLoyalUser(orderId, email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void sendMessage_throwsForbidden_whenSenderUserNotFound() {
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.of(order));
        when(securityUtils.getCurrentEmail()).thenReturn("unknown@test.com");
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(orderId, new OrderMessageRequest("Hola")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_usesSenderEmailFallback_whenNameIsNull() {
        sender.setFirstName(null);
        sender.setLastName(null);
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(orderId, companyId)).thenReturn(Optional.of(order));
        when(securityUtils.getCurrentEmail()).thenReturn("worker@test.com");
        when(userRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(sender));

        var saved = new OrderMessage();
        saved.setId(UUID.randomUUID());
        saved.setOrder(order);
        saved.setSender(sender);
        saved.setContent("Test");
        saved.setCreatedAt(Instant.now());
        when(messageRepository.save(any(OrderMessage.class))).thenReturn(saved);

        OrderMessageResponse result = service.sendMessage(orderId, new OrderMessageRequest("Test"));

        assertThat(result.senderName()).isEqualTo("worker@test.com");
    }
}
