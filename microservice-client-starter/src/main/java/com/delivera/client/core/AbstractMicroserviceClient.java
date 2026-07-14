package com.delivera.client.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.client.exception.ClientException;
import com.delivera.client.exception.ServerException;
import com.delivera.client.proxy.ClientResponse;



import reactor.core.publisher.Mono;


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

    
    public <R> Mono<ClientResponse<R>> exchange(
        String url,
        HttpMethod method,
        Object body,
        Map<String, String> headers,
        Class<R> responseType, 
        Boolean isFailOn4xx,
        Boolean isFailOn5xx
    ) {
        
        var request = webClient.method(method)
        .uri(url)
        .headers(h -> {
            h.setAll(headers);
            h.setContentType(MediaType.APPLICATION_JSON);
        });


        WebClient.RequestHeadersSpec<?> finalRequest;

        
        if (body != null) {
            finalRequest = request.bodyValue(body);
        } else {
            finalRequest = request;
        }
        return finalRequest.exchangeToMono(response -> {

                int status = response.statusCode().value();

                
               

                if (status >= 400 && status < 500 && isFailOn4xx) {
                    Mono<String> errorBody = response.bodyToMono(String.class)
                    .defaultIfEmpty("");
                    return errorBody.flatMap(bodyText ->
                        Mono.error(new ClientException(status, bodyText))
                    );
                }

                if (status >= 500 && isFailOn5xx) {
                    Mono<String> errorBody = response.bodyToMono(String.class)
                    .defaultIfEmpty("");
                    return errorBody.flatMap(bodyText ->
                        Mono.error(new ServerException(status, bodyText))
                    );
                }


                Map<String, String> responseHeaders = new HashMap<>();
                response.headers().asHttpHeaders().forEach(
                    (k, v) -> responseHeaders.put(k, v.get(0))
                );

                
                return response.bodyToMono(responseType)
                .map(bodyResp ->
                    new ClientResponse<>(status, responseHeaders, bodyResp)
                )
                .switchIfEmpty(Mono.just(
                    new ClientResponse<>(status, responseHeaders, null)
                ));

            });
    }

    public <R> Mono<ClientResponse<R>> exchange(
        String url,
        HttpMethod method,
        Object body,
        Map<String, String> headers,
        ParameterizedTypeReference<R> responseType, 
        Boolean isFailOn4xx,
        Boolean isFailOn5xx
    ) {
        
        var request = webClient.method(method)
        .uri(url)
        .headers(h -> {
            h.setAll(headers);
            h.setContentType(MediaType.APPLICATION_JSON);
        });


        WebClient.RequestHeadersSpec<?> finalRequest;

        
        if (body != null) {
            finalRequest = request.bodyValue(body);
        } else {
            finalRequest = request;
        }
        return finalRequest.exchangeToMono(response -> {

                int status = response.statusCode().value();

                
               

                if (status >= 400 && status < 500 && isFailOn4xx) {
                    Mono<String> errorBody = response.bodyToMono(String.class)
                    .defaultIfEmpty("");
                    return errorBody.flatMap(bodyText ->
                        Mono.error(new ClientException(status, bodyText))
                    );
                }

                if (status >= 500 && isFailOn5xx) {
                    Mono<String> errorBody = response.bodyToMono(String.class)
                    .defaultIfEmpty("");
                    return errorBody.flatMap(bodyText ->
                        Mono.error(new ServerException(status, bodyText))
                    );
                }


                Map<String, String> responseHeaders = new HashMap<>();
                response.headers().asHttpHeaders().forEach(
                    (k, v) -> responseHeaders.put(k, v.get(0))
                );

                
                return response.bodyToMono(responseType)
                .map(bodyResp ->
                    new ClientResponse<>(status, responseHeaders, bodyResp)
                )
                .switchIfEmpty(Mono.just(
                    new ClientResponse<>(status, responseHeaders, null)
                ));

            });
    }




}

