package com.delivera.order.service;

import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.order.dto.DataOrderRequest;
import com.delivera.order.dto.DataOrderStatusRequest;
import com.delivera.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderClient {

    private final SmartMicroserviceClient client;
    private final SecurityUtils securityUtils;
    private final Integer RETRIRES = 3;
    private final Integer TIMEOUT = 5000;


    public OrderResponse executeB2CRequest(DataOrderRequest requestBody) {
        return client.request()
        .service("data-service")
        .path("/internal/orders/B2C")
        .method(HttpMethod.POST)
        .header("Authorization","Bearer "+securityUtils.getCurrentJwt())
        .mtls()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(OrderResponse.class)
        .block();
    }
    /* 
    public String claimOrder(String token,String email, UUID loyalUserId) {
        return client.request()
        .service("data-service")
        .path("/internal/orders/public/track/"+token+"/register/"+loyalUserId)
        .method(HttpMethod.POST)
        .mtls()
        .body(email)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(String.class)
        .block();
    }*/

    public UUID createSeed(DataOrderRequest requestBody,  String url) {
        return client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(UUID.class).block();
    }


    public Void createStatusSeed(DataOrderStatusRequest requestBody,  String url) {
        return client.request()
        .url(url)
        .method(HttpMethod.POST)
        .http()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }


   
}
