package com.delivera.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.delivera.auth.dto.ChangeUsernameRequest;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RegisterRequest;
import com.delivera.auth.dto.RegisterRequestSeed;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.model.Credential;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.RefreshTokenService;

@ExtendWith (MockitoExtension.class)
class AuthInternalControllerTest {

    @Mock
    private AuthServiceImpl authService;

    @Mock 
    private RefreshTokenService refreshTokenService;

    @InjectMocks 
    private AuthInternalController controller;

    private UUID userId;
    private UUID companyId;

    @BeforeEach 
    void setup() {

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        ReflectionTestUtils.setField(
                controller,
                "secureRefreshCookie",
                false
        );

        ReflectionTestUtils.setField(
                controller,
                "domainRefreshCookie",
                "localhost"
        );

        ReflectionTestUtils.setField(
                controller,
                "pathRefreshCookie",
                "/"
        );
    }

    private Credential buildCredential() {

        Credential credential =
                new Credential();

        credential.setUserId(userId);
        credential.setEmail("test@test.com");

        return credential;
    }
    @Test
    void registerSeed_shouldCreateCredential() {

        RegisterRequestSeed request =
                new RegisterRequestSeed();

        request.setUserId(userId);
        request.setEmail("test@test.com");
        request.setUsername("user");
        request.setPassword("pwd");

        ResponseEntity<Void> response =
                controller.registerSeed(request);

        verify(authService)
                .createCredentials(
                        userId,
                        "test@test.com",
                        "user",
                        "pwd"
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
    @Test
    void register_shouldReturnLoginResponseWithoutRefreshToken() {

        RegisterRequest request =
                new RegisterRequest();

        request.setUserId(userId);
        request.setEmail("test@test.com");
        request.setUsername("user");
        request.setPassword("pwd");

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        request.setContext(context);

        Credential credential =
                buildCredential();

        LoginResponse loginResponse =
        new LoginResponse("","",UUID.randomUUID(),"","","","",null);

        when(
                authService.createCredentials(
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(credential);

        when(
                authService.buildLoginResponse(
                        credential,
                        context
                )
        ).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response =
                controller.register(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(loginResponse);

        verify(refreshTokenService, never())
                .create(any(), any(), any(), any(), any());
    }
    @Test
    void register_shouldCreateRefreshToken() {

        RegisterRequest request =
                new RegisterRequest();

        request.setUserId(userId);
        request.setEmail("test@test.com");
        request.setUsername("user");
        request.setPassword("pwd");

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        context.setCompanyId(companyId);

        request.setContext(context);

        RequestClientData clientData =
                new RequestClientData(
                        "127.0.0.1",
                        "device",
                        "Chrome"
                );

        request.setRequestClientData(clientData);

        Credential credential =
                buildCredential();

        LoginResponse loginResponse =
        new LoginResponse("","",UUID.randomUUID(),"","","","",null);


        when(
                authService.createCredentials(
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(credential);

        when(
                refreshTokenService.create(
                        credential,
                        "device",
                        "Chrome",
                        "127.0.0.1",
                        companyId
                )
        ).thenReturn("refresh-token");

        when(
                refreshTokenService.getDaysToRefresh()
        ).thenReturn(4);

        when(
                authService.buildLoginResponse(
                        eq(credential),
                        eq(context),
                        any(RefreshCookieData.class)
                )
        ).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response =
                controller.register(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(loginResponse);
    }
    @Test
    void register_shouldSupportNullContext() {

        RegisterRequest request =
                new RegisterRequest();

        request.setUserId(userId);
        request.setEmail("test@test.com");
        request.setPassword("pwd");

        request.setRequestClientData(
                new RequestClientData(
                        "ip",
                        "device",
                        "agent"
                )
        );

        Credential credential =
                buildCredential();

        when(
                authService.createCredentials(
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(credential);

        when(
                refreshTokenService.create(
                        eq(credential),
                        any(),
                        any(),
                        any(),
                        isNull()
                )
        ).thenReturn("token");

        when(
                refreshTokenService.getDaysToRefresh()
        ).thenReturn(4);

        when(
                authService.buildLoginResponse(
                        eq(credential),
                        isNull(),
                        any(RefreshCookieData.class)
                )
        ).thenReturn( 
            new LoginResponse("","",UUID.randomUUID(),"","","","",null)
        );

        ResponseEntity<LoginResponse> response =
                controller.register(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
    @Test
    void changeUsername_shouldReturnNoContent() {

        ChangeUsernameRequest request =
                new ChangeUsernameRequest(
                        "new-user",
                        userId
                );

        ResponseEntity<Void> response =
                controller.changeUsername(request);

        verify(authService)
                .changeUsername(
                        userId,
                        "new-user"
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
    @Test
    void deleteUser_shouldReturnNoContent() {
    
        ResponseEntity<Void> response =
                controller.deleteUser(userId);
    
        verify(authService)
                .delete(userId);
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
    @Test
    void deleteUsers_shouldReturnNoContent() {
    
        Set<UUID> userIds =
                Set.of(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );
    
        ResponseEntity<Void> response =
                controller.deleteUsers(userIds);
    
        verify(authService)
                .deleteUsers(userIds);
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}
