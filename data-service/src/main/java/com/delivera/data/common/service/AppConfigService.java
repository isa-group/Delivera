package com.delivera.data.common.service;




import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.data.common.dto.AppConfigResponse;
import com.delivera.data.common.dto.OrderPriorityConfigDto;
import com.delivera.data.common.dto.OrderStatusConfigDto;
import com.delivera.data.exception.InvalidStatusTransitionException;
import com.delivera.data.order.model.OrderStatusConfig;
import com.delivera.data.order.repository.OrderPriorityConfigRepository;
import com.delivera.data.order.repository.OrderStatusConfigRepository;


import java.util.List;

@Service
public class AppConfigService {

    private final com.delivera.data.order.repository.OrderStatusConfigRepository statusConfigRepository;
    private final OrderPriorityConfigRepository priorityConfigRepository;


    public AppConfigService(OrderStatusConfigRepository statusConfigRepository,
                            OrderPriorityConfigRepository priorityConfigRepository
    ) {
        this.statusConfigRepository = statusConfigRepository;
        this.priorityConfigRepository = priorityConfigRepository;
    }


   

    @Transactional(readOnly = true)
    public AppConfigResponse getConfig() {
        List<OrderStatusConfigDto> statuses = statusConfigRepository.findAllByOrderBySortOrderAsc()
                .stream().map(OrderStatusConfigDto::from).toList();
        List<OrderPriorityConfigDto> priorities = priorityConfigRepository.findAllByOrderBySortOrderAsc()
                .stream().map(OrderPriorityConfigDto::from).toList();
       
        return new AppConfigResponse(statuses, priorities);
    }

    @Transactional(readOnly = true)
    public void validateTransition(String current, String next) {
        OrderStatusConfig config = statusConfigRepository.findById(current)
                .orElseThrow(InvalidStatusTransitionException::new);
        if (config.isTerminal() || !config.getAllowedTransitionsList().contains(next)) {
            throw new InvalidStatusTransitionException();
        }
    }
}
