package com.delivera.client.proxy;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpMethod;

import lombok.Getter;



@Getter
public class ClientRequestBuilder {

    private String url;
    private HttpMethod method = HttpMethod.GET;
    private Map<String, String> headers = new HashMap<>();
    private Object body;

    private boolean internal = false;
    private boolean forceHttp = false;
    private boolean forceMtls = false;

    
    private int retries = 0;
    private long timeoutMs = -1;
    private boolean logEnabled = false;
    private boolean failOn4xx = true;
    private boolean failOn5xx = true;


    public ClientRequestBuilder url(String url) {
        this.url = url;
        return this;
    }

    public ClientRequestBuilder method(HttpMethod method) {
        this.method = method;
        return this;
    }

    public ClientRequestBuilder header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public ClientRequestBuilder body(Object body) {
        this.body = body;
        return this;
    }

    public ClientRequestBuilder useInternal() {
        this.internal = true;
        return this;
    }

    public ClientRequestBuilder http() {
        this.forceHttp = true;
        this.forceMtls = false;
        return this;
    }

    public ClientRequestBuilder mtls() {
        this.forceMtls = true;
        this.forceHttp = false;
        return this;
    }


    public boolean isInternal() {
        return internal;
    }

    public boolean isHttp() {
        return forceHttp;
    }

    public boolean isMtls() {
        return forceMtls;
    }

    
    public ClientRequestBuilder retry(int retries) {
        this.retries = retries;
        return this;
    }

    public ClientRequestBuilder timeout(long millis) {
        this.timeoutMs = millis;
        return this;
    }

    public ClientRequestBuilder log() {
        this.logEnabled = true;
        return this;
    }

    public ClientRequestBuilder failOn4xx(boolean value) {
        this.failOn4xx = value;
        return this;
    }

    public ClientRequestBuilder failOn5xx(boolean value) {
        this.failOn5xx = value;
        return this;
    }


    

}

