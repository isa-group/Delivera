package com.delivera.service;

import com.delivera.auth.service.AuthClient;
import com.delivera.dto.user.ProfileResponse;
import com.delivera.dto.user.UpdateProfileRequest;
import com.delivera.exception.UsernameAlreadyExistsException;
import com.delivera.exception.UserNotFoundException;
import com.delivera.model.User;
import com.delivera.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AppConfigService appConfigService;
    private final AuthClient client;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        return ProfileResponse.from(user);
    }

    @Transactional
    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        String originalUsername = user.getUsername();
        if (request.username() != null && !request.username().isBlank()) {
            if (!request.username().equals(user.getUsername()) && userRepository.existsByUsername(request.username())) {
                throw new UsernameAlreadyExistsException();
            }
            user.setUsername(request.username());
        }
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone() : null);
        user.setAddress(StringUtils.hasText(request.address()) ? request.address() : null);
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        User savedUser = userRepository.save(user);
        if (!request.username().equals(originalUsername)) {
            client.changeUsername(savedUser.getId(),savedUser.getUsername()).block();
        }

        return ProfileResponse.from(savedUser);
    }

    @Transactional
    public ProfileResponse updateAvatar(String email, String avatarData) {
        appConfigService.checkUploadSize(avatarData);
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        user.setAvatarData(avatarData);
        userRepository.save(user);
        return ProfileResponse.from(user);
    }

    /* 
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("CURRENT_PASSWORD_INVALID");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("NEW_PASSWORD_SAME_AS_CURRENT");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }*/

}
