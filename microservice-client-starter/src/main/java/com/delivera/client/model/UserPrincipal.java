package com.delivera.client.model;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPrincipal {

    private UUID userId;
    private UUID companyId;
    private String email;
    private String role;
    private Integer tokenVersion;

    public UserPrincipal(UUID userId, UUID companyId, String email, String role, Integer tokenVersion) {
        this.userId = userId;
        this.companyId = companyId;
        this.email = email;
        this.role = role;
        this.tokenVersion = tokenVersion;
    }


}
