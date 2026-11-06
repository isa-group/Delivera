package com.delivera.data.space.service;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.data.space.dto.SpaceRequestJwt;

import io.github.isagroup.spaceclient.SpaceClient;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpaceGeneralService {

    private final SpaceClient spaceClient;

    private final SmartMicroserviceClient client;


    public SpaceRequestJwt getToken(String orgId, boolean serverEval) {
        String url = this.spaceClient.getHttpUrl() + "/features/"+orgId+"/pricing-token?server="+serverEval;
        String apiKey = this.spaceClient.getApiKey();
        SpaceRequestJwt token = client.request()
        .url(url)
        .header("x-api-key",apiKey)
        .method(HttpMethod.POST)
        .http()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(SpaceRequestJwt.class).block();

        return token;
    }
}
