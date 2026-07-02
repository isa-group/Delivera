package com.delivera.dto.auth;

import java.util.UUID;

import com.delivera.auth.dto.RefreshCookieData;
import com.fasterxml.jackson.annotation.JsonInclude;

public record CompanyRegisterResponse(
        String token,
        String email,
        UUID companyId,
        String role,
        String companyName,
        String orgHandle,
        String orgName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        RefreshCookieData refreshCookieData
) {
        public CompanyRegisterResponse( String token,
                String email,
                UUID companyId,
                String role,
                String companyName,
                String orgHandle,
                String orgName
        ) {
                this(token, email, companyId, role, companyName, orgHandle, orgName, null);
        }

        public CompanyRegisterResponse deleteRefreshCookie() {
                return new CompanyRegisterResponse(
                        token,
                        email,
                        companyId,
                        role,
                        companyName,
                        orgHandle,
                        orgName
                );
        }
}
