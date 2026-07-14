package com.delivera.dto.auth;

import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterResponse {
    private String token;
    private String email;
    private String role;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    RefreshCookieData refreshCookieData;

    public RegisterResponse(String token, String email, String role) {
        this.token = token;
        this.email = email;
        this.role = role;
    }

    public RegisterResponse(String token, String email, String role, RefreshCookieData refreshCookieData) {
        this.token = token;
        this.email = email;
        this.role = role;
        this.refreshCookieData = refreshCookieData;
    }


}
