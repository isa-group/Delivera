package com.delivera.client.config;


import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.client.config.properties.MtlsProperties;
import com.delivera.client.core.MtlsMicroserviceClient;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.http.client.HttpClient;


import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;


@Configuration
@EnableConfigurationProperties(MtlsProperties.class)
@ConditionalOnProperty(
    prefix = "delivera.mtls",
    name = "enabled",
    havingValue = "true"
)
public class MtlsAutoConfig {

    @Bean("mtlsWebClient")
    public WebClient mtlsWebClient(MtlsProperties config) throws Exception {

                
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

        Resource keyStoreResource = resourceLoader.getResource(config.getKeyStore());
        Resource trustStoreResource = resourceLoader.getResource(config.getTrustStore());

            
        KeyStore keyStore = KeyStore.getInstance(config.getKeyStoreType());
        try (InputStream is = keyStoreResource.getInputStream()) {
            keyStore.load(is, config.getKeyStorePassword().toCharArray());
        }


        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, config.getKeyStorePassword().toCharArray());

       
        KeyStore trustStore = KeyStore.getInstance(config.getKeyStoreType());
        try (InputStream is = trustStoreResource.getInputStream()) {
            trustStore.load(is, config.getTrustStorePassword().toCharArray());
        }


        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(kmf)
                .trustManager(tmf)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public MtlsMicroserviceClient mtlsClient(
            @Qualifier("mtlsWebClient") WebClient webClient) {
        return new MtlsMicroserviceClient(webClient);
    }
}
