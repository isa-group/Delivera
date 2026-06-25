package com.delivera.client.proxy;

import org.springframework.http.HttpMethod;

import com.delivera.client.core.SmartMicroserviceClient;


import reactor.core.publisher.Mono;

public class ClientRequestExecutor {
    
    private final ClientRequestBuilder builder = new ClientRequestBuilder();
    private final SmartMicroserviceClient client;

    public ClientRequestExecutor(SmartMicroserviceClient client) {
        this.client = client;
    }

    public ClientRequestExecutor url(String url) {
        builder.url(url);
        return this;
    }

    public ClientRequestExecutor method(HttpMethod method) {
        builder.method(method);
        return this;
    }

    public ClientRequestExecutor header(String key, String value) {
        builder.header(key, value);
        return this;
    }

    public ClientRequestExecutor body(Object body) {
        builder.body(body);
        return this;
    }

    public ClientRequestExecutor internal() {
        builder.useInternal();
        return this;
    }

    public ClientRequestExecutor http() {
        builder.http();
        return this;
    }

    public ClientRequestExecutor mtls() {
        builder.mtls();
        return this;
    }

        
    public ClientRequestExecutor retry(int retries) {
        builder.retry(retries);
        return this;
    }

    
    public ClientRequestExecutor retryDelay(int retryDelayMs) {
        builder.retryDelay(retryDelayMs);
        return this;
    }


    public ClientRequestExecutor timeout(long millis) {
        builder.timeout(millis);
        return this;
    }

    public ClientRequestExecutor log() {
        builder.log();
        return this;
    }

    public ClientRequestExecutor failOn4xx(boolean value) {
        builder.failOn4xx(value);
        return this;
    }

    public ClientRequestExecutor failOn5xx(boolean value) {
        builder.failOn5xx(value);
        return this;
    }

    
    public ClientRequestExecutor service(String serviceName) {
        builder.service(serviceName);
        return this;
    }

    public ClientRequestExecutor path(String path) {
       builder.path(path);
        return this;
    }


    public <R> Mono<R> executeBasicRequest(Class<R> responseType) {
        return client.excuteBasicRequest(builder, responseType);
    }
    

    public <R> Mono<ClientResponse<R>> execute(Class<R> responseType) {
        return client.execute(builder, responseType);
    }

    

    
}
