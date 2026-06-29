package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.auth.dto.RegisterRequest;
import com.delivera.client.core.SmartMicroserviceClient;



import reactor.core.publisher.Mono;


@Service
public class AuthClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public AuthClient(SmartMicroserviceClient client) {
        this.client = client;
    }

    public Mono<Object> registerSeed(UUID userId, String email, String username,String password, String url) {
        RegisterRequest registerRequest = buildRegisterRequest(
            userId, email, username, password
        );
        System.out.println(registerRequest.getUserId()+","+
        registerRequest.getEmail()+","+
         registerRequest.getUsername()+","+
         registerRequest.getPassword());
        return client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(registerRequest)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(1000)
        .log()
        .executeBasicRequest(Object.class);
    } 
    
    
    public Mono<Object> register(UUID userId, String email, String username,String password) {
        RegisterRequest registerRequest = buildRegisterRequest(
            userId, email, username, password
        );
        return client.request()
        .service("auth-service")
        .path("/internal/auth/register")
        .method(HttpMethod.POST)
        .mtls()
        .body(registerRequest)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(1000)
        .log()
        .executeBasicRequest(Object.class);
    } 

    private RegisterRequest buildRegisterRequest(UUID userId, String email, String username,String password) {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUserId(userId);
        registerRequest.setEmail(email);
        registerRequest.setUsername(username);
        registerRequest.setPassword(password);
        return registerRequest;
    }

}
