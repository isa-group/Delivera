package com.delivera.space.dto;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractChange {
    private String plan;

    private Map<String, ChangeAddOn> newAddOns;

}
