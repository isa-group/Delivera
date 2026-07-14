package com.delivera.client.core;

import java.util.Map;

import reactor.core.publisher.Mono;

public interface MicroserviceClient {

    <T, R> Mono<R> postAsync(
        String url,
        T body,
        Class<R> responseType,
        Map<String, String> headers
    );

    <R> Mono<R> getAsync(
        String url,
        Class<R> responseType,
        Map<String, String> headers
    );

    <T, R> Mono<R> putAsync(
        String url, 
        T body, 
        Class<R> responseType, 
        Map<String, String> headers
    );

    <R> Mono<R> deleteAsync(
        String url, 
        Class<R> responseType, 
        Map<String, String> headers
    );



    <T, R> R post(
        String url,
        T body,
        Class<R> responseType,
        Map<String, String> headers
    );

    <R> R get(
        String url,
        Class<R> responseType,
        Map<String, String> headers
    );

    <T, R> R put(
        String url, 
        T body, 
        Class<R> responseType, 
        Map<String, String> headers
    );

    <R> R delete(
        String url, 
        Class<R> responseType, 
        Map<String, String> headers
    );

}
