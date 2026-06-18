package com.delivera.auth.service;

import java.util.UUID;

import com.delivera.auth.model.Credential;

public interface AuthService {

    void createCredentials(UUID userId, String email, String username, String password);

    Credential login(String identifier, String password, String ip);
}
