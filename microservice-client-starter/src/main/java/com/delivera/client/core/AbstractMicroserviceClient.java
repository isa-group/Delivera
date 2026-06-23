package com.delivera.client.core;

import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
public abstract class AbstractMicroserviceClient implements MicroserviceClient {

    protected final WebClient webClient;

    protected AbstractMicroserviceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public <R> Mono<R> getAsync(
        String url,
        Class<R> responseType,
        Map<String, String> headers
    ) {
        return webClient.get()
            .uri(url)
            .headers(h -> h.setAll(headers))
            .retrieve()
            .bodyToMono(responseType);
    }


    public <T, R> Mono<R> postAsync(
        String url,
        T body,
        Class<R> responseType,
        Map<String, String> headers
    ) {
        return webClient.post()
            .uri(url)
            .headers(h -> h.setAll(headers))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(responseType);
    }

    public <T, R> Mono<R> putAsync(
        String url,
        T body,
        Class<R> responseType,
        Map<String, String> headers
    ) {
        return webClient.put()
            .uri(url)
            .headers(h -> h.setAll(headers))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(responseType);
    }

    public <R> Mono<R> deleteAsync(
        String url,
        Class<R> responseType,
        Map<String, String> headers
    ) {
        return webClient.delete()
            .uri(url)
            .headers(h -> h.setAll(headers))
            .retrieve()
            .bodyToMono(responseType);
    }


    @Override
    public <T, R> R post(String url, T body, Class<R> responseType, Map<String, String> headers) {
        return postAsync(url, body, responseType, headers).block();
    }

    @Override
    public <R> R get(String url, Class<R> responseType, Map<String, String> headers) {
        return getAsync(url, responseType, headers).block();
    }

    @Override
    public <T, R> R put(String url, T body, Class<R> responseType, Map<String, String> headers) {
        return putAsync(url, body, responseType, headers).block();
    }

    @Override
    public <R> R delete(String url, Class<R> responseType, Map<String, String> headers) {
        return deleteAsync(url, responseType, headers).block();
    }

   

   

   
    
    

     

    

}

