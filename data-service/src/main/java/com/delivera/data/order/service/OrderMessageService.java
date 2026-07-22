package com.delivera.data.order.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.exception.OrderNotFoundException;
import com.delivera.data.order.dto.OrderMessageRequest;
import com.delivera.data.order.dto.OrderMessageResponse;
import com.delivera.data.order.model.Order;
import com.delivera.data.order.model.OrderMessage;
import com.delivera.data.order.repository.OrderMessageRepository;
import com.delivera.data.order.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderMessageService {

    private final OrderMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    // TODO:P006-User private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    public OrderMessageService(OrderMessageRepository messageRepository,
                               OrderRepository orderRepository,
                               //TODO UserRepository userRepository,
                               SecurityUtils securityUtils) {
        this.messageRepository = messageRepository;
        this.orderRepository = orderRepository;
        // TODO:P006-User this.userRepository = userRepository;
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
        UUID senderId = securityUtils.getCurrentUserId();
        /* TODO:P006-User * User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Sender not found"));  */
        // TODO: PENSAR SI QUEREMOS GUARDAR UUID Y EMAIL DEL SENDER 
        OrderMessage message = new OrderMessage();

        message.setOrder(order);
        message.setSender(senderId);
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
