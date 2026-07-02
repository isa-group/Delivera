package com.delivera.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.exception.InvalidCredentialsException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.model.RefreshToken;
import com.delivera.auth.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${app.refresh-token.daysToRefresh}")
    private  Integer daysToRefresh = 1;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public Integer getDaysToRefresh() {
        return daysToRefresh;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RefreshToken build(
        Credential credential, 
        String device,
        String userAgent, 
        String ip,
        UUID tokenId,
        String secret,
        Boolean suspicious
    ) {
        RefreshToken refreshToken = new RefreshToken();
        String secretHash = hash(secret);

        refreshToken.setId(tokenId);
        refreshToken.setSecretHash(secretHash);
        refreshToken.setCredential(credential);
        refreshToken.setIp(ip);
        refreshToken.setDevice(device);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setExpiredAt(LocalDateTime.now().plusDays(daysToRefresh));
        refreshToken.setLastUsed(LocalDateTime.now());
        refreshToken.setSuspicious(suspicious);

        return refreshToken;
    }



    private String createToken(
        Credential credential, 
        String device,
        String userAgent, 
        String ip,
        boolean suspicious
    ) {
       
        UUID tokenId = UUID.randomUUID();
        String secret = UUID.randomUUID().toString();

        String rawToken = tokenId.toString() + "." + secret;

        RefreshToken refreshToken = build(
            credential, device, userAgent, ip, 
            tokenId, secret, suspicious
        );
       
        repository.save(refreshToken);

        return rawToken;
    }

    @Transactional
    public String create(
        Credential credential, 
        String device,
        String userAgent, 
        String ip
    ){
        return createToken(credential, device, userAgent, ip, false);
    }

    private String[] validateFormat(String token) {
        if (token == null || !token.contains(".")) {
            throw new InvalidCredentialsException();
        }

        String[] parts = token.split("\\.");

        
        if (parts.length != 2) {
            throw new InvalidCredentialsException();
        }
        return parts;
    }

    private void validateExpired(RefreshToken refreshToken) {
        
        if (refreshToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            repository.delete(refreshToken); 
            throw new InvalidCredentialsException();
        }

    }

    private void validateSecret(String secret, RefreshToken refreshToken) {
        if (!MessageDigest.isEqual(
            hash(secret).getBytes(),
            refreshToken.getSecretHash().getBytes())
        ){
            throw new InvalidCredentialsException();
        }
    }

    private UUID parseUUID(String rawUUID) {
        try {
            return UUID.fromString(rawUUID);
        }catch(IllegalArgumentException e) {
            throw new InvalidCredentialsException();
        } 
    }



    private RefreshToken validate(String token) {
       
        String[] parts = validateFormat(token);
    
        UUID tokenId = parseUUID(parts[0]);
        String secret = parts[1];

        RefreshToken refreshToken = repository.findValidById(tokenId)
        .orElseThrow(() -> new InvalidCredentialsException());

        validateSecret(secret,refreshToken);
        validateExpired(refreshToken);

        return refreshToken;

    }

    @Transactional(readOnly = true)
    public RefreshToken getFromToken(String token) {
        return validate(token);
    }

    private boolean isSuspicious(RefreshToken refreshToken, String device, String userAgent) {
        return refreshToken.getSuspicious() 
        || (
            !refreshToken.getDevice().equals(device)
            && !refreshToken.getUserAgent().equals(userAgent)
        );
    }

    @Transactional
    public String refresh(
        String token,
        String device,
        String userAgent, 
        String ip
    ) {
        RefreshToken refreshToken = validate(token);
        boolean suspicious = isSuspicious(refreshToken, device, userAgent);
        String newToken = createToken(
           refreshToken.getCredential(),
           device,
           userAgent,
           ip, 
           suspicious
        );
        repository.delete(refreshToken);
        return newToken;
    }


    @Transactional
    public void removeToken(
        String token
    ) {
        RefreshToken refreshToken = validate(token);
        repository.delete(refreshToken);
    }


    @Transactional
    public void notSuspicious(
        RefreshToken refreshToken
    ) {
        refreshToken.setSuspicious(true);
        repository.save(refreshToken);
    }




}
