package com.delivera.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.exception.InvalidRefreshTokenException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.model.RefreshToken;
import com.delivera.auth.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${app.refresh-token.daysToRefresh}")
    private  Integer daysToRefresh = 4;

    @Value("${app.refresh-token.maxDaysToRefresh}")
    private Integer maxDaysToRefresh = 30;

    @Value("${app.refresh-token.periodicDeletionMs}")
    private final long  periodicDeletionMs = 1000 * 60;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @Scheduled(fixedRate = periodicDeletionMs)
    public void removeExpiredTokens() {
        log.info("PERIODIC TOKEN REFRESH DELETION AT {}",Instant.now().atZone(ZoneId.systemDefault()));
        repository.deleteByExpiredTokens(Instant.now());
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
        Boolean suspicious,
        Instant maxExpiredAt
    ) {
        RefreshToken refreshToken = new RefreshToken();
        String secretHash = hash(secret);

        refreshToken.setId(tokenId);
        refreshToken.setSecretHash(secretHash);
        refreshToken.setCredential(credential);
        refreshToken.setIp(ip);
        refreshToken.setDevice(device);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setExpiredAt(Instant.now().plus(daysToRefresh,ChronoUnit.DAYS));
        refreshToken.setLastUsed(Instant.now());
        refreshToken.setMaxExpiredAt(maxExpiredAt);
        refreshToken.setSuspicious(suspicious);

        return refreshToken;
    }



    private String createToken(
        Credential credential, 
        String device,
        String userAgent, 
        String ip,
        Boolean suspicious,
        Instant maxExpiredAt
    ) {
       
        UUID tokenId = UUID.randomUUID();
        String secret = UUID.randomUUID().toString();

        String rawToken = tokenId.toString() + "." + secret;

        RefreshToken refreshToken = build(
            credential, device, userAgent, ip, 
            tokenId, secret, suspicious, maxExpiredAt
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
        return createToken(
            credential, 
            device, 
            userAgent, 
            ip, 
            false, 
            Instant.now().plus(maxDaysToRefresh, ChronoUnit.DAYS)
        );
    }

    private String[] validateFormat(String token) {
        if (token == null || !token.contains(".")) {
            throw new InvalidRefreshTokenException();
        }

        String[] parts = token.split("\\.");

        
        if (parts.length != 2) {
            throw new InvalidRefreshTokenException();
        }
        return parts;
    }

    private void validateExpired(RefreshToken refreshToken) {
        
        if (
            refreshToken.getExpiredAt().isBefore(Instant.now())
            || refreshToken.getMaxExpiredAt().isBefore(Instant.now())
        ) {
            repository.delete(refreshToken); 
            throw new InvalidRefreshTokenException();
        }

    }

    private void validateSecret(String secret, RefreshToken refreshToken) {
        if (!MessageDigest.isEqual(
            hash(secret).getBytes(StandardCharsets.UTF_8),
            refreshToken.getSecretHash().getBytes(StandardCharsets.UTF_8))
        ){
            throw new InvalidRefreshTokenException();
        }
    }

    private UUID parseUUID(String rawUUID) {
        try {
            return UUID.fromString(rawUUID);
        }catch(IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        } 
    }



    private RefreshToken validate(String token) {
       
        String[] parts = validateFormat(token);
    
        UUID tokenId = parseUUID(parts[0]);
        String secret = parts[1];

        RefreshToken refreshToken = repository.findValidById(tokenId)
        .orElseThrow(() -> new InvalidRefreshTokenException());

        validateSecret(secret,refreshToken);
        validateExpired(refreshToken);

        return refreshToken;

    }

    @Transactional(readOnly = true)
    public RefreshToken validateAndGet(String token) {
        return validate(token);
    }

    @Transactional
    public RefreshToken use(
        String token,
        String device,
        String userAgent, 
        String ip
    ) {
        RefreshToken refreshToken = validateAndGet(token);
        refreshToken.setLastUsed(Instant.now());
        refreshToken.setIp(ip);
        refreshToken.setDevice(device);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setSuspicious(isSuspicious(refreshToken, device, userAgent));
        return repository.save(refreshToken);
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
           suspicious,
           refreshToken.getMaxExpiredAt()
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
        refreshToken.setSuspicious(false);
        repository.save(refreshToken);
    }


    

}
