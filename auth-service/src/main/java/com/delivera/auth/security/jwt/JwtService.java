package com.delivera.auth.security.jwt;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.delivera.auth.config.JwtKeyProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private final JwkProvider jwkProvider;
    private final KeyRotationService rotationService;
    private final JwtKeyProperties properties;

    @Value("${app.jwt.issuer}")
    private  String issuer;

    @Value("${app.jwt.audience}")
    private  String audience;

    @Autowired
    public JwtService(JwkProvider jwkProvider,
                      KeyRotationService rotationService,
                      JwtKeyProperties properties) {
        this.jwkProvider = jwkProvider;
        this.rotationService = rotationService;
        this.properties = properties;
    }

    public String generateToken(UUID userId,
                                UUID companyId,
                                String role,
                                int tokenVersion) {

        String keyId = rotationService.resolveActiveKeyIdSafe();
        RSAKey key = jwkProvider.getActiveKey(keyId);

        if (key == null) {
            throw new IllegalStateException("Key not found: " + keyId);
        }

        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getExpiration());

        RSAPrivateKey privateKey;

        try {
            privateKey = key.toRSAPrivateKey();
        } catch (JOSEException e) {
            throw new RuntimeException("Invalid RSA key", e);
        }

        return Jwts.builder()
                .header()
                    .add("kid", keyId)
                .and()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("companyId", companyId.toString())
                .claim("role", role)
                .claim("ver", tokenVersion)
                .issuedAt(now)
                .expiration(exp)
                .signWith(privateKey)
                .compact();
    }
}
