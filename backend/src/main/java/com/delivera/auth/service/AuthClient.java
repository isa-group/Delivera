package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.auth.dto.ChangeUsernameRequest;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.RegisterRequestAuth;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.dto.auth.LoginResponse;

import reactor.core.publisher.Mono;


@Service
public class AuthClient {

    private final SmartMicroserviceClient client;

    @Autowired
    public AuthClient(SmartMicroserviceClient client) {
        this.client = client;
    }

    public  Mono<LoginResponse> registerBase(RegisterRequestAuth registerRequest) {
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
        .timeout(5000)
        .log()
        .executeBasicRequest(LoginResponse.class);

    }


    public Mono<Void> registerSeed(UUID userId, String email, String username,String password, String url) {
        RegisterRequestAuth registerRequest = buildRegisterRequest(
            userId, email, username, password
        );
        return client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(registerRequest)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(Void.class);
    } 
    
    
    public Mono<LoginResponse> register(UUID userId, String email, String username,String password) {
        RegisterRequestAuth registerRequest = buildRegisterRequest(
            userId, email, username, password
        );
        return registerBase(registerRequest);
        
    } 

    public Mono<LoginResponse> register(
        UUID userId, String email, String username,String password,
        DeliveraOrgContext context, RequestClientData requestClientData
    ) {
        RegisterRequestAuth registerRequest = buildRegisterRequest(
            userId, email, username, password
        );
        registerRequest.setContext(context);
        registerRequest.setRequestClientData(requestClientData);
        
        return registerBase(registerRequest);
    }

    private RegisterRequestAuth buildRegisterRequest(UUID userId, String email, String username,String password) {
        RegisterRequestAuth registerRequest = new RegisterRequestAuth();
        registerRequest.setUserId(userId);
        registerRequest.setEmail(email);
        registerRequest.setUsername(username);
        registerRequest.setPassword(password);
        return registerRequest;
    }


    public Mono<Void> changeUsername(UUID userId, String username) {
        ChangeUsernameRequest body = new ChangeUsernameRequest(username, userId);
        return client.request()
        .service("auth-service")
        .path("/internal/auth/username")
        .method(HttpMethod.PUT)
        .mtls()
        .body(body)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(Void.class);
        
    }

    public Mono<Void> deleteUser(UUID userId) {
        return client.request()
        .service("auth-service")
        .path("/internal/auth/user/"+userId)
        .method(HttpMethod.DELETE)
        .mtls()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(Void.class);
        
    }

}
