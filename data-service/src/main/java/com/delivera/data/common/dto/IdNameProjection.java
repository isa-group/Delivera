package com.delivera.data.common.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class IdNameProjection {

    private UUID id;

    private String name;

}
