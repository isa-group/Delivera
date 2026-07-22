package com.delivera.data.order.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ClaimData {

    private String email;
    private UUID companyId;
    private String address;
    private ClaimRegisterRequest request;
    private RequestClientData clientData;

}
