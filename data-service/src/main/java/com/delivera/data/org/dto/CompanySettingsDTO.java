package com.delivera.data.org.dto;

import java.util.UUID;

import com.delivera.data.order.model.OrderPriority;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanySettingsDTO {


    private UUID companyId;

    private UUID orgId;

    private OrderPriority defaultPriority;

    private boolean defaultPriorityLocked = false;

}
