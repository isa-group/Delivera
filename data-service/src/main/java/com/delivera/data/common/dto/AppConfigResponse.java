package com.delivera.data.common.dto;

import java.util.List;

public record AppConfigResponse(
        List<OrderStatusConfigDto> orderStatuses,
        List<OrderPriorityConfigDto> orderPriorities
) {}
