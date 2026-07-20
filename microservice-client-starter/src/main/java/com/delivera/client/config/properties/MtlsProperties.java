package com.delivera.client.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "delivera.mtls")
public class MtlsProperties {

    private boolean enabled;
    private int port;

    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType  = "PKCS12";

    private String trustStore;
    private String trustStorePassword;
}

