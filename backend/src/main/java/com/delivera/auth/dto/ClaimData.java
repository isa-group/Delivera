package com.delivera.auth.dto;

import java.util.UUID;

import com.delivera.dto.auth.ClaimRegisterRequest;

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
