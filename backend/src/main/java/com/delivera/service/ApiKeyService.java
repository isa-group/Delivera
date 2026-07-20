package com.delivera.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.exception.ApiKeyNotFoundException;
import com.delivera.exception.CompanyContextException;
import com.delivera.model.ApiKey;
import com.delivera.org.dto.ApiKeyCreateRequest;
import com.delivera.org.dto.ApiKeyCreatedResponse;
import com.delivera.org.dto.ApiKeyResponse;
import com.delivera.org.model.Company;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String TOKEN_PREFIX = "dlv_";
    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_PREFIX_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return apiKeyRepository.findByCompanyIdOrderByCreatedAtDesc(securityUtils.getCurrentCompanyId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApiKeyCreatedResponse create(ApiKeyCreateRequest req) {
        Company company = companyRepository.findById(securityUtils.getCurrentCompanyId())
                .orElseThrow(CompanyContextException::new);

        String token = generateToken();
        String prefix = token.substring(0, VISIBLE_PREFIX_LENGTH);

        ApiKey key = new ApiKey();
        key.setCompany(company);
        key.setName(req.name());
        key.setPrefix(prefix);
        key.setKeyHash(hash(token));
        apiKeyRepository.save(key);

        return new ApiKeyCreatedResponse(key.getId(), key.getName(), key.getPrefix(), token, Instant.now());
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey key = apiKeyRepository.findByIdAndCompanyId(id, securityUtils.getCurrentCompanyId())
                .orElseThrow(ApiKeyNotFoundException::new);
        if (key.getRevokedAt() == null) {
            key.setRevokedAt(Instant.now());
            apiKeyRepository.save(key);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private ApiKeyResponse toResponse(ApiKey k) {
        return new ApiKeyResponse(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), k.getRevokedAt(), k.getLastUsedAt());
    }
}
