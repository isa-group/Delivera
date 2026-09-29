package com.delivera.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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

import com.delivera.auth.dto.ChangePasswordRequest;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.Device;
import com.delivera.auth.dto.LoginRequest;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.SwitchCompanyRequest;
import com.delivera.auth.dto.ValidatePassword;
import com.delivera.auth.model.Credential;
import com.delivera.auth.model.RefreshToken;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.service.AuthServiceImpl;
import com.delivera.auth.service.DeliveraClient;
import com.delivera.auth.service.RefreshTokenService;
import com.delivera.client.config.properties.SecurityUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

@ExtendWith (MockitoExtension.class)
class AuthControllerTest {

    @Mock 
    private DeliveraClient client;

    @Mock
    private AuthServiceImpl authService;

    @Mock
    private InMemoryAuthRateLimiter authRateLimiter;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks 
    private AuthController controller;

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setup() {

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        ReflectionTestUtils.setField(
                controller,
                "activeGateway",
                false
        );

        ReflectionTestUtils.setField(
                controller,
                "secureRefreshCookie",
                false
        );

        ReflectionTestUtils.setField(
                controller,
                "domainRefreshCookie",
                null
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
        credential.setTokenVersion(0);

        return credential;
    }

    private RefreshToken buildRefreshToken() {

        RefreshToken refreshToken =
                new RefreshToken();

        refreshToken.setId(UUID.randomUUID());
        refreshToken.setCredential(buildCredential());
        refreshToken.setCompanyId(companyId);

        return refreshToken;
    }
    @Test
    void login_shouldReturnCookieAndLoginResponse() {

        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setIdentifier("user");
        loginRequest.setPassword("pwd");

        Credential credential =
                buildCredential();

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        context.setCompanyId(companyId);

        LoginResponse response =
        new LoginResponse("","",UUID.randomUUID(),"","","","",null);

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(request.getHeader("X-Device-Id"))
                .thenReturn("device");

        when(request.getHeader("User-Agent"))
                .thenReturn("Chrome");

        when(
                authService.login(
                        "user",
                        "pwd",
                        "127.0.0.1"
                )
        ).thenReturn(credential);

        when(
                client.getOrgInfoByUserId(userId)
        ).thenReturn(Mono.just(context));

        when(
                authService.buildLoginResponse(
                        credential,
                        context
                )
        ).thenReturn(response);

        when(
                refreshTokenService.create(
                        eq(credential),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(companyId)
                )
        ).thenReturn("token");

        when(
                refreshTokenService.getDaysToRefresh()
        ).thenReturn(4);

        when(
            request.getHeader("X-Forwarded-For")
        ).thenReturn(null);

        ResponseEntity<LoginResponse> result =
                controller.login(
                        request,
                        loginRequest
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
                .isSameAs(response);

        verify(authRateLimiter)
                .check("LOGIN:127.0.0.1");
    }
    @Test
    void switchCompany_shouldReturnLoginResponse() {

        RefreshToken refreshToken =
                buildRefreshToken();

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        LoginResponse response =
                new LoginResponse("","",UUID.randomUUID(),"","","","",null);

        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );

        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(securityUtils.getTokenVersion())
                .thenReturn(0);

        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);

        when(
                client.getOrgSwitchInfo(
                        userId,
                        companyId
                )
        ).thenReturn(Mono.just(context));

        when(
                authService.buildLoginResponse(
                        refreshToken.getCredential(),
                        context
                )
        ).thenReturn(response);

        SwitchCompanyRequest requestBody =
                new SwitchCompanyRequest(companyId);

        ResponseEntity<LoginResponse> result =
                controller.switchCompany(
                        request,
                        requestBody
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
                .isSameAs(response);
    }
    @Test
    void changePassword_shouldReturnUpdatedLogin() {

        RefreshToken refreshToken =
                buildRefreshToken();

        Credential credential =
                buildCredential();

        DeliveraOrgContext context =
                new DeliveraOrgContext();

        LoginResponse response =
            new LoginResponse("","",UUID.randomUUID(),"","","","",null);
        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );

        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(securityUtils.getTokenVersion())
                .thenReturn(0);

        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);

        when(
                authService.changePassword(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(credential);

        when(
                client.getOrgSwitchInfo(
                        userId,
                        companyId
                )
        ).thenReturn(Mono.just(context));

        when(
                authService.buildLoginResponse(
                        credential,
                        context
                )
        ).thenReturn(response);

        ChangePasswordRequest body =
                new ChangePasswordRequest(
                        "old",
                        "new"
                );

        ResponseEntity<LoginResponse> result =
                controller.changePassword(
                        request,
                        body
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void refresh_shouldReturnNewToken() {
    
        RefreshToken refreshToken =
                buildRefreshToken();
    
        DeliveraOrgContext context =
                new DeliveraOrgContext();
    
        LoginResponse response =
        new LoginResponse("","",UUID.randomUUID(),"","","","",null);
    
        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );
    
        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});
    
        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");
    
        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);
    
        when(
                client.getOrgSwitchInfo(
                        userId,
                        companyId
                )
        ).thenReturn(Mono.just(context));
    
        when(
                authService.buildLoginResponse(
                        refreshToken.getCredential(),
                        context
                )
        ).thenReturn(response);
    
        when(
                refreshTokenService.refresh(
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn("new-token");
    
        when(
                refreshTokenService.getDaysToRefresh()
        ).thenReturn(4);
    
        ResponseEntity<LoginResponse> result =
                controller.refresh(request);
    
        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void refresh_shouldBuildLoyalUserContext() {
    
        RefreshToken refreshToken =
                buildRefreshToken();
    
        refreshToken.setCompanyId(null);
    
        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );
    
        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});
    
        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");
    
        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);
    
        when(
                refreshTokenService.refresh(
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn("new-token");
    
        when(
                authService.buildLoginResponse(
                        eq(refreshToken.getCredential()),
                        any(DeliveraOrgContext.class)
                )
        ).thenReturn(new LoginResponse("","",UUID.randomUUID(),"","","","",null));
    
        when(
                refreshTokenService.getDaysToRefresh()
        ).thenReturn(4);
    
        ResponseEntity<LoginResponse> result =
                controller.refresh(request);
    
        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void logout_shouldDeleteToken() {
    
        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );
    
        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});
    
        ResponseEntity<Void> result =
                controller.logout(request);
    
        verify(refreshTokenService)
                .removeToken("token");
    
        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void logout_shouldWorkWithoutToken() {

        when(request.getCookies())
                .thenReturn(null);

        ResponseEntity<Void> result =
                controller.logout(request);

        verify(refreshTokenService, never())
                .removeToken(any());

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void devices_shouldReturnDevices() {

        RefreshToken refreshToken =
                buildRefreshToken();

        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );

        List<Device> devices =
                List.of();

        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);

        when(
                refreshTokenService.getDevices(
                        userId,
                        refreshToken.getId()
                )
        ).thenReturn(devices);

        ResponseEntity<List<Device>> result =
                controller.devices(request);

        assertThat(result.getBody())
                .isSameAs(devices);
    }
    @Test
    void deleteOthersRefresh_shouldReturnNoContent() {

        RefreshToken refreshToken =
                buildRefreshToken();

        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );

        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);

        ResponseEntity<Void> result =
                controller.deleteOthersRefresh(
                        request,
                        new ValidatePassword("pwd")
                );

        verify(refreshTokenService)
                .removeOthersTokens(refreshToken);

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
    @Test
    void revokeOthersRefresh_shouldReturnNoContent() {
    
        RefreshToken refreshToken =
                buildRefreshToken();
    
        Cookie cookie =
                new Cookie(
                        "refresh_token",
                        "token"
                );
    
        when(request.getCookies())
                .thenReturn(new Cookie[]{cookie});
    
        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");
    
        when(
                refreshTokenService.validateAndGet("token")
        ).thenReturn(refreshToken);
    
        ResponseEntity<Void> result =
                controller.revokeOthersRefresh(
                        request,
                        new ValidatePassword("pwd")
                );
    
        verify(refreshTokenService)
                .revokeOthersTokens(refreshToken);
    
        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}