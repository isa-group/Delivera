package com.delivera.auth.service;

import java.util.UUID;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.model.Credential;

public interface AuthService {

    Credential createCredentials(UUID userId, String email, String username, String password);

    Credential login(String identifier, String password, String ip);

    Credential getUserCredentialByEmail(String email, Integer tokenVersion);

    Credential changePassword(UUID userId,String rawPreviousPassword,String rawNewPassword, Integer tokenVersion);

    LoginResponse buildLoginResponse(Credential credential, DeliveraOrgContext orgInfo);
    LoginResponse buildLoginResponse(Credential credential, DeliveraOrgContext orgInfo, RefreshCookieData refreshCookie);

    Credential changeUsername(UUID userId, String username);

    void delete(UUID userId);
}
