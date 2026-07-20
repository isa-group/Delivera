package com.delivera.client.core;



import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;


public class MtlsMicroserviceClient extends AbstractMicroserviceClient {

    public MtlsMicroserviceClient(@Qualifier("mtlsWebClient") WebClient webClient) {
        super(webClient);
    }

}