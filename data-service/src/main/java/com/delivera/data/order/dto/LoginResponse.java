package com.delivera.data.order.dto;

import java.util.UUID;


import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponse {
        private String token;
        private String email;
        private UUID companyId;
        private String role;
        private String companyName;
        private String orgHandle;
        private String orgName;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private UUID LoyalUserId; 
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private RefreshCookieData refreshCookie;
}