package com.delivera.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.auth.service.AuthService;

@ExtendWith (MockitoExtension.class)
class AdminInternalControllerTest {

    @Mock 
    private AuthService authService;

    @InjectMocks 
    private AdminInternalController controller;

    private UUID userId;

    @BeforeEach 
    void setup() {
        userId = UUID.randomUUID();
    }

    @Test
    void deleteAllExceptUserId_shouldReturnNoContent() {

        ResponseEntity<Void> response =
                controller.deleteAllExceptUserId(
                        userId
                );

        verify(authService)
                .deleteAllExceptUserId(
                        userId
                );

        assertThat(response.getStatusCode())
                .isEqualTo(
                        HttpStatus.NO_CONTENT
                );
    }
}
