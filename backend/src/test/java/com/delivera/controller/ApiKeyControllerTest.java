package com.delivera.controller;

import com.delivera.org.dto.ApiKeyCreateRequest;
import com.delivera.org.dto.ApiKeyCreatedResponse;
import com.delivera.org.dto.ApiKeyResponse;
import com.delivera.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock private ApiKeyService apiKeyService;
    @InjectMocks private ApiKeyController controller;

    @Test
    void list_returns200WithKeys() {
        ApiKeyResponse key = new ApiKeyResponse(UUID.randomUUID(), "My Key", "dlv_", null, null, null);
        when(apiKeyService.list()).thenReturn(List.of(key));
        var resp = controller.list();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void create_returns201WithCreatedKey() {
        ApiKeyCreateRequest req = new ApiKeyCreateRequest("ERP Key");
        ApiKeyCreatedResponse created = new ApiKeyCreatedResponse(UUID.randomUUID(), "ERP Key", "dlv_", "dlv_secret_token", null);
        when(apiKeyService.create(req)).thenReturn(created);
        var resp = controller.create(req);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isSameAs(created);
    }

    @Test
    void revoke_returns204() {
        UUID id = UUID.randomUUID();
        doNothing().when(apiKeyService).revoke(id);
        assertThat(controller.revoke(id).getStatusCode().value()).isEqualTo(204);
        verify(apiKeyService).revoke(id);
    }
}
