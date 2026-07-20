package com.delivera.auth.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.delivera.auth.repository.CredentialRepository;
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.jwt.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    /*
    @Mock
    private final CredentialRepository credentialRepository;
    @Mock
    private final PasswordEncoder passwordEncoder;
    @Mock
    private final AuthRateLimiter rateLimiter;
    @Mock
    private final JwtService jwtService;

    @BeforeEach
    void setUp() {

    }
     */


}
