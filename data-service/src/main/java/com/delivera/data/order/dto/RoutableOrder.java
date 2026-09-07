package com.delivera.data.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RoutableOrder {

    private UUID id;

    private BigDecimal lat;

    private BigDecimal lng;

}
