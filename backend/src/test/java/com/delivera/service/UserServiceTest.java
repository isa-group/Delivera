package com.delivera.service;

import com.delivera.auth.service.AuthClient;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.user.ProfileResponse;
import com.delivera.dto.user.UpdateProfileRequest;
import com.delivera.exception.UserNotFoundException;
import com.delivera.exception.UsernameAlreadyExistsException;
import com.delivera.model.User;
import com.delivera.repository.UserRepository;

import reactor.core.publisher.Mono;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private AuthClient client;
    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("user@test.com");
        user.setUsername("testuser");
    }

    @Test
    void getProfile_found_returnsProfileResponse() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        ProfileResponse result = userService.getProfile("user@test.com");
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    void updateProfile_success() {
        UpdateProfileRequest req = new UpdateProfileRequest("newusername", "John", "Doe", null, null, null, null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newusername")).thenReturn(false);
        when(client.changeUsername(any(), any()))
        .thenReturn(Mono.empty());
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile("user@test.com", req);
        verify(userRepository).save(user);
    }

  
    @Test
    void getProfile_notFound_throws() {
        when(userRepository.findByEmail("x@t.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getProfile("x@t.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_usernameConflict_throws() {
        UpdateProfileRequest req = new UpdateProfileRequest("taken", "A", "B", null, null, null, null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        assertThatThrownBy(() -> userService.updateProfile("user@test.com", req))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void updateAvatar_success() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        assertThat(userService.updateAvatar("user@test.com", "data:image/png;base64,XX")).isNotNull();
    }

}
