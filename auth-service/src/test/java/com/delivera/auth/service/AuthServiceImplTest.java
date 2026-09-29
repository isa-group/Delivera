package com.delivera.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.exception.EmailAlreadyExistsException;
import com.delivera.auth.exception.ForbiddenException;
import com.delivera.auth.exception.InvalidCredentialsException;
import com.delivera.auth.exception.UserNotFoundException;
import com.delivera.auth.exception.UsernameAlreadyExistsException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.repository.CredentialRepository;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.security.jwt.JwtService;

@ExtendWith (MockitoExtension.class)
class AuthServiceImplTest {

    @Mock 
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InMemoryAuthRateLimiter rateLimiter;

    @Mock
    private JwtService jwtService;

    @InjectMocks 
    private AuthServiceImpl service;

    private UUID userId;
    private String email;
    private String username;
    private String password;

    @BeforeEach 
    void setup() {

        userId = UUID.randomUUID();

        email = "user@test.com";

        username = "user";

        password = "Password123!";
    }

    private Credential buildCredential() {

        Credential credential =
                new Credential();

        credential.setUserId(userId);
        credential.setEmail(email);
        credential.setUsername(username);
        credential.setPasswordHash("HASH");
        credential.setTokenVersion(0);

        return credential;
    }
    @Test
    void createCredentials_shouldCreateCredential() {

        Credential credential =
                buildCredential();

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByUsername(username)
        ).thenReturn(Optional.empty());

        when(
                passwordEncoder.encode(password)
        ).thenReturn("HASH");

        when(
                credentialRepository.save(any())
        ).thenReturn(credential);

        Credential result =
                service.createCredentials(
                        userId,
                        email,
                        username,
                        password
                );

        assertThat(result)
                .isSameAs(credential);
    }
    @Test
    void createCredentials_shouldThrowWhenUserIdExists() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(
                Optional.of(buildCredential())
        );

        assertThatThrownBy(
                () -> service.createCredentials(
                        userId,
                        email,
                        username,
                        password
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void createCredentials_shouldThrowWhenEmailExists() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(
                Optional.of(buildCredential())
        );

        assertThatThrownBy(
                () -> service.createCredentials(
                        userId,
                        email,
                        username,
                        password
                )
        ).isInstanceOf(
                EmailAlreadyExistsException.class
        );
    }
    @Test
    void createCredentials_shouldThrowWhenUsernameExists() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByUsername(username)
        ).thenReturn(
                Optional.of(buildCredential())
        );

        assertThatThrownBy(
                () -> service.createCredentials(
                        userId,
                        email,
                        username,
                        password
                )
        ).isInstanceOf(
                UsernameAlreadyExistsException.class
        );
    }
    @Test
    void createCredentials_shouldTranslateDataIntegrityViolationException() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByUsername(username)
        ).thenReturn(Optional.empty());

        when(
                passwordEncoder.encode(password)
        ).thenReturn("HASH");

        when(
                credentialRepository.save(any())
        ).thenThrow(
                new DataIntegrityViolationException("DB")
        );

        assertThatThrownBy(
                () -> service.createCredentials(
                        userId,
                        email,
                        username,
                        password
                )
        ).isInstanceOf(
                RuntimeException.class
        );
    }
    @Test
    void checkPassword_shouldAcceptCorrectPassword() {
    
        Credential credential =
                buildCredential();
    
        when(
                passwordEncoder.matches(
                        password,
                        credential.getPasswordHash()
                )
        ).thenReturn(true);
    
        service.checkPassword(
                password,
                credential
        );
    }
    @Test
    void checkPassword_shouldThrowWhenPasswordIsWrong() {
    
        Credential credential =
                buildCredential();
    
        when(
                passwordEncoder.matches(
                        password,
                        credential.getPasswordHash()
                )
        ).thenReturn(false);
    
        assertThatThrownBy(
                () -> service.checkPassword(
                        password,
                        credential
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void checkTokenVersion_shouldAcceptValidVersion() {
    
        Credential credential =
                buildCredential();
    
        service.checkTokenVersion(
                0,
                credential
        );
    }
    @Test
    void checkTokenVersion_shouldThrowInvalidCredentials() {
    
        Credential credential =
                buildCredential();
    
        assertThatThrownBy(
                () -> service.checkTokenVersion(
                        5,
                        credential
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void login_shouldThrowInvalidCredentialsWhenUserNotFound() {
    
        when(
                credentialRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(
                                email,
                                email
                        )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
                () -> service.login(
                        email,
                        password,
                        "127.0.0.1"
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void login_shouldThrowInvalidCredentialsWhenPasswordIsWrong() {
    
        Credential credential =
                buildCredential();
    
        when(
                credentialRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(
                                email,
                                email
                        )
        ).thenReturn(Optional.of(credential));
    
        when(
                passwordEncoder.matches(
                        password,
                        credential.getPasswordHash()
                )
        ).thenReturn(false);
    
        assertThatThrownBy(
                () -> service.login(
                        email,
                        password,
                        "127.0.0.1"
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void login_shouldReturnCredential() {

        Credential credential =
                buildCredential();

        when(
                credentialRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(
                                email,
                                email
                        )
        ).thenReturn(Optional.of(credential));

        when(
                passwordEncoder.matches(
                        password,
                        credential.getPasswordHash()
                )
        ).thenReturn(true);

        Credential result =
                service.login(
                        email,
                        password,
                        "127.0.0.1"
                );

        assertThat(result)
                .isSameAs(credential);

        verify(rateLimiter)
                .check("USER:" + email);
    }
    @Test
    void getUserCredentialByEmail_shouldReturnCredential() {
    
        Credential credential =
                buildCredential();
    
        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.of(credential));
    
        Credential result =
                service.getUserCredentialByEmail(
                        email,
                        0
                );
    
        assertThat(result)
                .isSameAs(credential);
    }
    @Test
    void getUserCredentialByEmail_shouldThrowUserNotFound() {

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getUserCredentialByEmail(
                        email,
                        0
                )
        ).isInstanceOf(
                UserNotFoundException.class
        );
    }
    @Test
    void changePassword_shouldThrowWhenPreviousPasswordIsWrong() {
    
        Credential credential =
                buildCredential();
    
        when(
                passwordEncoder.matches(
                        "wrong-password",
                        credential.getPasswordHash()
                )
        ).thenReturn(false);
    
        assertThatThrownBy(
                () -> service.changePassword(
                        credential,
                        "wrong-password",
                        "new-password",
                        0,
                        "127.0.0.1"
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    
        verify(rateLimiter)
                .check("FAIL_USER:" + credential.getEmail());
    
        verify(rateLimiter)
                .check("FAIL_IP:127.0.0.1");
    }
    @Test
    void changePassword_shouldThrowWhenTokenVersionIsInvalid() {
    
        Credential credential =
                buildCredential();
    
        when(
                passwordEncoder.matches(
                        "old-password",
                        credential.getPasswordHash()
                )
        ).thenReturn(true);
    
        assertThatThrownBy(
                () -> service.changePassword(
                        credential,
                        "old-password",
                        "new-password",
                        99,
                        "127.0.0.1"
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void changePassword_shouldUpdatePasswordAndVersion() {
    
        Credential credential =
                buildCredential();
    
        when(
                passwordEncoder.matches(
                        "old-password",
                        credential.getPasswordHash()
                )
        ).thenReturn(true);
    
        when(
                passwordEncoder.encode("new-password")
        ).thenReturn("NEW_HASH");
    
        when(
                credentialRepository.save(credential)
        ).thenReturn(credential);
    
        Credential result =
                service.changePassword(
                        credential,
                        "old-password",
                        "new-password",
                        0,
                        "127.0.0.1"
                );
    
        assertThat(result)
                .isSameAs(credential);
    
        assertThat(
                credential.getPasswordHash()
        ).isEqualTo("NEW_HASH");
    
        assertThat(
                credential.getTokenVersion()
        ).isEqualTo(1);
    
        verify(credentialRepository)
                .save(credential);
    }
    @Test
    void buildLoginResponse_shouldBuildResponseWithRefreshCookie() {

        UUID companyId = UUID.randomUUID();
        Credential credential =
                buildCredential();

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        context.setCompanyId(companyId);
        context.setRole("ADMIN");

        RefreshCookieData refreshCookie =
                mock(RefreshCookieData.class);

        when(
                jwtService.generateToken(
                        eq(userId),
                        eq(email),
                        eq(companyId),
                        eq("ADMIN"),
                        any(),
                        eq(0)
                )
        ).thenReturn("JWT");

        LoginResponse response =
                service.buildLoginResponse(
                        credential,
                        context,
                        refreshCookie
                );

        assertThat(response.getToken())
                .isEqualTo("JWT");

        assertThat(response.getEmail())
                .isEqualTo(email);

        assertThat(response.getRefreshCookie())
                .isSameAs(refreshCookie);
    }
    @Test
    void buildLoginResponse_shouldHandleNullContext() {

        Credential credential =
                buildCredential();

        when(
    jwtService.generateToken(
            any(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            anyInt()
        )
    ).thenReturn("JWT");
     

        LoginResponse response =
                service.buildLoginResponse(
                        credential,
                        null
                );

        assertThat(response.getToken())
                .isEqualTo("JWT");
    }
    @Test
    void changeUsername_shouldThrowForbiddenWhenCredentialNotFound() {

        when(
                credentialRepository.findById(userId)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.changeUsername(
                        userId,
                        "new-user"
                )
        ).isInstanceOf(
                ForbiddenException.class
        );
    }
    @Test
    void changeUsername_shouldThrowWhenUsernameAlreadyExists() {
    
        Credential credential =
                buildCredential();
    
        when(
                credentialRepository.findById(userId)
        ).thenReturn(Optional.of(credential));
    
        when(
                credentialRepository.findByUsername("new-user")
        ).thenReturn(Optional.of(buildCredential()));
    
        assertThatThrownBy(
                () -> service.changeUsername(
                        userId,
                        "new-user"
                )
        ).isInstanceOf(
                UsernameAlreadyExistsException.class
        );
    }
    @Test
    void changeUsername_shouldUpdateUsername() {
    
        Credential credential =
                buildCredential();
    
        when(
                credentialRepository.findById(userId)
        ).thenReturn(Optional.of(credential));
    
        when(
                credentialRepository.findByUsername("new-user")
        ).thenReturn(Optional.empty());
    
        when(
                credentialRepository.save(credential)
        ).thenReturn(credential);
    
        Credential result =
                service.changeUsername(
                        userId,
                        "new-user"
                );
    
        assertThat(result)
                .isSameAs(credential);
    
        assertThat(
                credential.getUsername()
        ).isEqualTo("new-user");
    }
    @Test
    void delete_shouldDeleteCredential() {
    
        Credential credential =
                buildCredential();
    
        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.of(credential));
    
        service.delete(userId);
    
        verify(credentialRepository)
                .delete(credential);
    }
    @Test
    void delete_shouldDoNothingWhenCredentialDoesNotExist() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        service.delete(userId);

        verify(credentialRepository, never())
                .delete(any());
    }
    @Test
    void deleteUsers_shouldDoNothingWhenSetIsEmpty() {
    
        service.deleteUsers(Set.of());
    
        verify(credentialRepository, never())
                .deleteUsers(any());
    }
    @Test
    void deleteUsers_shouldDeleteUsers() {
    
        Set<UUID> userIds =
                Set.of(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );
    
        service.deleteUsers(userIds);
    
        verify(credentialRepository)
                .deleteUsers(userIds);
    }
    @Test
    void deleteAllExceptUserId_shouldDelegateToRepository() {
    
        service.deleteAllExceptUserId(userId);
    
        verify(credentialRepository)
                .deleteAllExceptUserId(userId);
    }
    @Test
    void createCredentials_shouldThrowWhenUuidIsNull() {
    
        assertThatThrownBy(
                () -> service.createCredentials(
                        null,
                        email,
                        username,
                        password
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void createCredentials_shouldThrowWhenEmailIsNull() {
    
        assertThatThrownBy(
                () -> service.createCredentials(
                        userId,
                        null,
                        username,
                        password
                )
        ).isInstanceOf(
                InvalidCredentialsException.class
        );
    }
    @Test
    void createCredentials_shouldAllowNullUsername() {

        when(
                credentialRepository.findByUserId(userId)
        ).thenReturn(Optional.empty());

        when(
                credentialRepository.findByEmail(email)
        ).thenReturn(Optional.empty());

        when(
                passwordEncoder.encode(password)
        ).thenReturn("HASH");

        Credential credential =
                new Credential();

        when(
                credentialRepository.save(any(Credential.class))
        ).thenReturn(credential);

        Credential result =
                service.createCredentials(
                        userId,
                        email,
                        null,
                        password
                );

        assertThat(result)
                .isSameAs(credential);

        verify(credentialRepository, never())
                .findByUsername(anyString());
    }

}