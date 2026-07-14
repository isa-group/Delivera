package com.delivera.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.settings.ApiKeyCreateRequest;
import com.delivera.dto.settings.ApiKeyCreatedResponse;
import com.delivera.exception.ApiKeyNotFoundException;
import com.delivera.model.ApiKey;
import com.delivera.model.Company;
import com.delivera.repository.ApiKeyRepository;
import com.delivera.repository.CompanyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private SecurityUtils securityUtils;
    @InjectMocks private ApiKeyService apiKeyService;

    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        company = new Company();
        company.setId(companyId);
        company.setName("Acme");
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
    }

    @Test
    void createGeneratesUniqueTokenAndStoresHash() {
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        ApiKeyCreatedResponse res = apiKeyService.create(new ApiKeyCreateRequest("ERP"));

        assertThat(res.token()).startsWith("dlv_");
        assertThat(res.prefix()).hasSize(12);
        assertThat(res.token()).startsWith(res.prefix());

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("ERP");
        assertThat(saved.getCompany()).isSameAs(company);
        assertThat(saved.getKeyHash()).isEqualTo(ApiKeyService.hash(res.token()));
        assertThat(saved.getKeyHash()).isNotEqualTo(res.token());
    }

    @Test
    void listReturnsKeysForCurrentCompany() {
        ApiKey k = new ApiKey();
        k.setName("k1");
        when(apiKeyRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(k));

        var list = apiKeyService.list();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("k1");
    }

    @Test
    void revokeMarksKeyAsRevoked() {
        UUID id = UUID.randomUUID();
        ApiKey k = new ApiKey();
        k.setId(id);
        when(apiKeyRepository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(k));

        apiKeyService.revoke(id);

        assertThat(k.getRevokedAt()).isNotNull();
        verify(apiKeyRepository).save(k);
    }

    @Test
    void revokeIsIdempotent() {
        UUID id = UUID.randomUUID();
        ApiKey k = new ApiKey();
        Instant earlier = Instant.now().minusSeconds(60);
        k.setRevokedAt(earlier);
        when(apiKeyRepository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(k));

        apiKeyService.revoke(id);

        assertThat(k.getRevokedAt()).isEqualTo(earlier);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void revokeMissingKeyThrows() {
        UUID id = UUID.randomUUID();
        when(apiKeyRepository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(id))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }
}
