package com.delivera.auth.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;


import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtKeyProperties {

    private long expiration;

    private Rotation rotation;

    
    private String activeKeyId;

    private Map<String, KeyConfig> keys;

    @Getter
    @Setter
    public static class Rotation {
        private boolean enabled;
        private String period;
    }

    @Getter
    @Setter
    public static class KeyConfig {
        private String privateKey;
        private String publicKey;
    }


}
