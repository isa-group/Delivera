package com.delivera.data.common.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class IdCountProjection {

    private UUID id;

    private Long count;

}
