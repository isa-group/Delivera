package com.delivera.client.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.delivera.client.config.properties.DeliveraProperties;
import com.delivera.client.config.properties.DeliveraProperties.ServiceConfig;
import com.delivera.client.exception.NetworkException;
import com.delivera.client.exception.ServerException;
import com.delivera.client.proxy.ClientRequestBuilder;
import com.delivera.client.proxy.ClientRequestExecutor;
import com.delivera.client.proxy.ClientResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
public class SmartMicroserviceClient {

    private final HttpMicroserviceClient httpClient;
    private final MtlsMicroserviceClient mtlsClient;
    private final DeliveraProperties config;

    public SmartMicroserviceClient(
        HttpMicroserviceClient httpClient,
        MtlsMicroserviceClient mtlsClient,
        DeliveraProperties config
    ) {
        this.httpClient = httpClient;
        this.mtlsClient = mtlsClient;
        this.config = config;
    }

    public ClientRequestExecutor request() {
        return new ClientRequestExecutor(this);
    }

    private <R> Mono<R> extraRequestOptions(ClientRequestBuilder builder, Mono<R> request) {
        if (builder.isLogEnabled()) {
            String protocol = resolveProtocol(builder);
            request = request.doOnSubscribe(sub ->
                log.info("[{}] {} {}",protocol, builder.getMethod(), resolveUrl(builder))
            ).doOnSuccess(res ->
                log.info("[{}] Response received", protocol)
            ).doOnError(err ->
                log.error("[{}] Error -> {}",protocol, err.getMessage())
            );

        }

        if (builder.getTimeoutMs() > 0) {
            request = request.timeout(Duration.ofMillis(builder.getTimeoutMs()));
        }

        if (builder.getRetries() > 0) {
            request = request
                .retryWhen(
                    Retry.backoff(builder.getRetries(), Duration.ofMillis(builder.getRetryDelayMs()))
                        .jitter(0.5)
                        .filter(ex ->
                            ex instanceof NetworkException ||
                            isRetryableServerError(ex)
                        )
                );

        }
        


        request = request.onErrorMap(ex -> {

            if (ex instanceof WebClientRequestException) {
                return new NetworkException("Connection error: " + ex.getMessage(), ex);
            }
            return ex;
        }).onErrorMap(TimeoutException.class, ex ->
            new ServerException(503,"Timeout calling service")
        );


        return request;

    }


    
    
    public <R> Mono<ClientResponse<R>> execute(
        ClientRequestBuilder builder,
        Class<R> responseType
    ) {

        AbstractMicroserviceClient client = resolveClient(builder);

        addInternalHeaders(builder);

        validateRequest(builder);

        Map<String, String> headers = new HashMap<>(builder.getHeaders());
        return extraRequestOptions(builder, 
            client.exchange(
                resolveUrl(builder),
                builder.getMethod(),
                builder.getBody(),
                headers,
                responseType,
                builder.isFailOn4xx(),
                builder.isFailOn5xx()
            )
        );

    }


   
       
    public <R> Mono<R> excuteBasicRequest(ClientRequestBuilder builder, Class<R> responseType) {

        return execute(builder, responseType)
            .flatMap(resp -> resp.getBody() != null
                ? Mono.just(resp.getBody())
                : Mono.empty()
            );

    }






    private AbstractMicroserviceClient resolveClient(ClientRequestBuilder builder) {

        if (builder.isMtls()) {
            return mtlsClient != null ? mtlsClient : httpClient;
        }

        if (builder.isHttp()) {
            return httpClient;
        }

        if (builder.isInternal()) {
            return mtlsClient != null ? mtlsClient : httpClient;
        }

        return httpClient;
    }

    private void addInternalHeaders(ClientRequestBuilder builder) {
        if (!builder.isInternal()) {
            return;
        }

        var clientConfig = config.getClient();
        var defaultConfig = new ServiceConfig(); 
        defaultConfig.setApiInternalKey(null);

        String serviceName = clientConfig.getServiceName();
        String apiKey = config.getSecurity().getServices()
            .getOrDefault(serviceName,defaultConfig)
            .getApiInternalKey();
        
        if (apiKey == null) {
            throw new IllegalStateException(
                "No API key configured for service: " + serviceName
            );
        }

        builder.header(
            clientConfig.getInternalAuth().getServiceHeaderName(),
            serviceName
        );

        builder.header(
            clientConfig.getInternalAuth().getHeaderName(),
            apiKey
        );
    }

    
    private void validateRequest(ClientRequestBuilder builder) {

        String url = resolveUrl(builder);

        if (builder.isMtls() && url.startsWith("http://")) {
            throw new IllegalStateException(
                "mTLS requires HTTPS URL. Invalid URL: " + url
            );
        }
    }

    private String resolveProtocol(ClientRequestBuilder builder) {

        String url = resolveUrl(builder);
    
        boolean isHttps = url.startsWith("https://");
        boolean isHttp = url.startsWith("http://");
    
        if (builder.isMtls()) {
            return "mTLS";
        }
    
        if (isHttps) {
            return "HTTPS";
        }
    
        if (isHttp) {
            return "HTTP";
        }
    
        return "UNKNOWN";
    }

    
    private String resolveUrl(ClientRequestBuilder builder) {

        if (builder.getUrl() != null) {
            return builder.getUrl();
        }

        String serviceName = builder.getServiceName();

        String baseUrl = config.getClient()
            .getServiceHosts()
            .get(serviceName);
        
        
        String basePath = config.getClient()
            .getServiceBasePaths()
            .getOrDefault(serviceName, "");


        if (baseUrl == null) {
            throw new IllegalStateException(
                "No host configured for service: " + serviceName
            );
        }

        String path = builder.getPath() != null ? builder.getPath() : "";

        return baseUrl +  basePath  + path;
    }

    
    
    private boolean isRetryableServerError(Throwable ex) {

        if (!(ex instanceof ServerException)) {
            return false;
        }

        int status = ((ServerException) ex).getStatus();

        return status == 502 || status == 503 || status == 504;
    }




}
