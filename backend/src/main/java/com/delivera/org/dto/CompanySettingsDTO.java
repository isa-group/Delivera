package com.delivera.org.dto;

import java.util.UUID;

import com.delivera.order.model.OrderPriority;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanySettingsDTO {


    private UUID companyId;

    private OrderPriority defaultPriority;

    private boolean defaultPriorityLocked = false;

}
