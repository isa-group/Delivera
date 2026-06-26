package com.delivera.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.chat.OrderMessageRequest;
import com.delivera.dto.chat.OrderMessageResponse;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.OrderNotFoundException;
import com.delivera.model.Order;
import com.delivera.model.OrderMessage;
import com.delivera.model.User;
import com.delivera.repository.OrderMessageRepository;
import com.delivera.repository.OrderRepository;
import com.delivera.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderMessageService {

    private final OrderMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    public OrderMessageService(OrderMessageRepository messageRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository,
                               SecurityUtils securityUtils) {
        this.messageRepository = messageRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
    }

    public List<OrderMessageResponse> getMessages(UUID orderId) {
        resolveOrder(orderId);
        return messageRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream()
                .map(OrderMessageResponse::from)
                .toList();
    }

    @Transactional
    public OrderMessageResponse sendMessage(UUID orderId, OrderMessageRequest request) {
        Order order = resolveOrder(orderId);

        String email = securityUtils.getCurrentEmail();
        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Sender not found"));

        OrderMessage message = new OrderMessage();

        message.setOrder(order);
        message.setSender(sender);
        message.setContent(request.content());
        return OrderMessageResponse.from(messageRepository.save(message));
    }

    private Order resolveOrder(UUID orderId) {
        String role = securityUtils.getCurrentRole();
        String email = securityUtils.getCurrentEmail();
        if ("LOYAL_USER".equals(role)) {
            return orderRepository.findByIdForLoyalUser(orderId, email)
                    .orElseThrow(OrderNotFoundException::new);
        }
        UUID companyId = securityUtils.getCurrentCompanyId();
        return orderRepository.findByIdForCompany(orderId, companyId)
                .orElseThrow(OrderNotFoundException::new);
    }
}
