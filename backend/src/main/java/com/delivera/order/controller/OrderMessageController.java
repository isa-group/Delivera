package com.delivera.order.controller;

import com.delivera.dto.chat.OrderMessageRequest;
import com.delivera.dto.chat.OrderMessageResponse;
import com.delivera.order.service.OrderMessageService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders/{orderId}/messages")
public class OrderMessageController {

    private final OrderMessageService service;

    public OrderMessageController(OrderMessageService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderMessageResponse> getMessages(@PathVariable UUID orderId) {
        return service.getMessages(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderMessageResponse sendMessage(@PathVariable UUID orderId,
                                            @Valid @RequestBody OrderMessageRequest request) {
        return service.sendMessage(orderId, request);
    }
}
