package com.delivera.controller;

import com.delivera.auth.service.AuthService;
import com.delivera.dto.common.AvailabilityCheckResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private OrganizationController controller;

    @Test
    void checkHandle_available_returns200True() {
        when(authService.isHandleAvailable("acme")).thenReturn(true);
        var resp = controller.checkHandle("acme");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(new AvailabilityCheckResponse(true));
    }

    @Test
    void checkHandle_taken_returns200False() {
        when(authService.isHandleAvailable("taken")).thenReturn(false);
        assertThat(controller.checkHandle("taken").getBody()).isEqualTo(new AvailabilityCheckResponse(false));
    }
}
