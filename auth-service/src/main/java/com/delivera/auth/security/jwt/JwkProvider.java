package com.delivera.auth.security.jwt;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.delivera.auth.config.JwtKeyProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;



@Component
public class JwkProvider {

    private final Map<String, RSAKey> keys = new HashMap<>();

    private final JwtKeyProperties properties;

    private final KeyLoader loader;

    

    @Autowired
    public JwkProvider(JwtKeyProperties properties, KeyLoader loader) throws Exception {

        this.properties = properties;
        this.loader = loader;

        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("JWT keys NOT loaded from YAML");
        }

        for (var entry : properties.getKeys().entrySet()) {

            String keyId = entry.getKey();
            var config = entry.getValue();

            RSAPublicKey publicKey = (RSAPublicKey) loader.loadPublicKey(config.getPublicKey());
            RSAPrivateKey privateKey = (RSAPrivateKey) loader.loadPrivateKey(config.getPrivateKey());

            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();

            keys.put(keyId, rsaKey);
        }
    }

    public RSAKey getActiveKey(String keyId) {
        return keys.get(keyId);
    }

    public JWKSet getJwkSet() {
        return new JWKSet(
                keys.values().stream()
                .map(RSAKey::toPublicJWK)
                .collect(Collectors.toList())        
        );
    }
}
