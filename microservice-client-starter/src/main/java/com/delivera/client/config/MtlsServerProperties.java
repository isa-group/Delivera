package com.delivera.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "delivera.mtls.server")
public class MtlsServerProperties {

    private boolean enabled;

    private int port;

    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType = "PKCS12";

    private String trustStore;
    private String trustStorePassword;

    private boolean useTempFile = true;
}
