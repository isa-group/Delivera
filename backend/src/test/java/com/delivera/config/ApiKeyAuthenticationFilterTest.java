package com.delivera.config;

import com.delivera.model.ApiKey;
import com.delivera.org.model.Company;
import com.delivera.repository.ApiKeyRepository;
import com.delivera.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private FilterChain filterChain;
    @InjectMocks private ApiKeyAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ApiKey keyFor(UUID companyId) {
        Company c = new Company();
        c.setId(companyId);
        ApiKey k = new ApiKey();
        k.setId(UUID.randomUUID());
        k.setCompany(c);
        return k;
    }

    @Test
    void missingHeader_proceedsWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void validKey_setsAuthenticationAndUpdatesLastUsedAt() throws Exception {
        UUID companyId = UUID.randomUUID();
        ApiKey key = keyFor(companyId);
        when(apiKeyRepository.findByKeyHash(ApiKeyService.hash("dlv_token")))
                .thenReturn(Optional.of(key));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "dlv_token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getDetails()).isEqualTo(companyId);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_API_KEY");
        assertThat(key.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(key);
    }

    @Test
    void unknownKey_returns401() throws Exception {
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "bad");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(res.getContentAsString()).contains("API_KEY_INVALID");
    }

    @Test
    void revokedKey_returns401() throws Exception {
        ApiKey key = keyFor(UUID.randomUUID());
        key.setRevokedAt(Instant.now());
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(key));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "revoked");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void blankHeader_proceedsWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "  ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verifyNoInteractions(apiKeyRepository);
    }
}
