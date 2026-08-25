package com.delivera.org.dto;

import java.util.UUID;

import com.delivera.order.model.OrderPriority;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanySettingsDTO {


    private UUID companyId;
    private UUID orgId;

    private OrderPriority defaultPriority;

    private boolean defaultPriorityLocked = false;

}
