package com.delivera.controller;

import com.delivera.dto.common.MessageResponse;
import com.delivera.dto.user.ChangePasswordRequest;
import com.delivera.dto.user.ProfileResponse;
import com.delivera.dto.user.UpdateProfileRequest;
import com.delivera.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private Authentication auth;
    @InjectMocks private UserController controller;

    private static ProfileResponse profile() {
        return new ProfileResponse(null, "u@e.com", "user1", "First", "Last", null, null, null, null, null, null);
    }

    @Test
    void getProfile_delegatesAndReturns200() {
        when(auth.getName()).thenReturn("u@e.com");
        when(userService.getProfile("u@e.com")).thenReturn(profile());
        var resp = controller.getProfile(auth);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(userService).getProfile("u@e.com");
    }

    @Test
    void updateProfile_delegatesAndReturns200() {
        when(auth.getName()).thenReturn("u@e.com");
        UpdateProfileRequest req = new UpdateProfileRequest("user1", "First", "Last", "600000000", null, null, null);
        when(userService.updateProfile("u@e.com", req)).thenReturn(profile());
        var resp = controller.updateProfile(auth, req);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateAvatar_delegatesAndReturns200() {
        when(auth.getName()).thenReturn("u@e.com");
        when(userService.updateAvatar("u@e.com", "base64")).thenReturn(profile());
        var resp = controller.updateAvatar(auth, Map.of("data", "base64"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    /* TODO: DELETE
    @Test
    void changePassword_delegatesAndReturns200WithMessage() {
        when(auth.getName()).thenReturn("u@e.com");
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass1a", "NewPass2b");
        doNothing().when(userService).changePassword("u@e.com", req);
        var resp = controller.changePassword(auth, req);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(new MessageResponse("PASSWORD_CHANGED"));
    }
        */
}
