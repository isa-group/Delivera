package com.delivera.controller;

import com.delivera.auth.controller.AuthController;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.service.AuthService;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.auth.*;
import com.delivera.dto.common.AvailabilityCheckResponse;
import com.delivera.security.AuthRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuthRateLimiter authRateLimiter;
    @Mock private HttpServletRequest httpRequest;
    @InjectMocks private AuthController controller;

    private static LoginResponse loginResp() {
        return new LoginResponse("tok", "u@e.com", null, "COMPANY_ADMIN", "Acme", "acme", "Acme Org",null);
    }

    /* 
    @Test
    void login_checksRateLimitAndDelegates() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        LoginRequest req = new LoginRequest("u@e.com", "Pass1a2B");
        when(authService.login("u@e.com", "Pass1a2B")).thenReturn(loginResp());

        var resp = controller.login(httpRequest, req);

        verify(authRateLimiter).check("127.0.0.1", "login");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
    */

    @Test
    void register_checksRateLimitAndDelegates() {
        // RegisterRequest(email, username, firstName, lastName, phone, password)
        RegisterRequest req = new RegisterRequest("u@e.com", "user1", "First", null, null, "Pass1a2B");
        RegisterResponse expected = new RegisterResponse("tok", "u@e.com", "LOYAL_USER");
        when(authService.getIp(any())).thenReturn("127.0.0.1");
        when(authService.refreshCookie(any())).thenReturn(ResponseCookie.from("saasa").build());
        when(authService.register(eq(req),any())).thenReturn(expected);

        var resp = controller.register(httpRequest, req);

        verify(authRateLimiter).check("127.0.0.1", "register");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(expected);
    }

    @Test
    void registerCompany_checksRateLimitAndReturns201() {
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        // CompanyRegisterRequest(email, password, orgName, orgHandle, companyName, activityType, username, firstName, lastName, phone)
        CompanyRegisterRequest req = new CompanyRegisterRequest(
                "admin@e.com", "Pass1a2B", "OrgName", "org-handle",
                "Company", "LOGISTICS", "admin", "First", "Last", "600000000");
        CompanyRegisterResponse expected = new CompanyRegisterResponse("tok", "admin@e.com", null, "COMPANY_ADMIN", "Company", "org-handle", "OrgName");
        when(authService.registerCompany(eq(req),any())).thenReturn(expected);
        when(authService.refreshCookie(any())).thenReturn(ResponseCookie.from("saasa").build());
        var resp = controller.registerCompany(httpRequest, req);

        verify(authRateLimiter).check("10.0.0.1", "register-company");
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().email()).isSameAs(expected.email());
        assertThat(resp.getBody().token()).isSameAs(expected.token());
        assertThat(resp.getBody().companyId()).isSameAs(expected.companyId());
        assertThat(resp.getBody().role()).isSameAs(expected.role());
        assertThat(resp.getBody().companyName()).isSameAs(expected.companyName());
    }

    @Test
    void checkUsername_returns200WithAvailability() {
        when(authService.isUsernameAvailable("admin")).thenReturn(true);
        var resp = controller.checkUsername("admin");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(new AvailabilityCheckResponse(true));
    }

    /*
    @Test
    void switchCompany_delegatesToAuthService() {
        UUID companyId = UUID.randomUUID();
        when(securityUtils.getCurrentEmail()).thenReturn("u@e.com");
        when(authService.switchCompany("u@e.com", companyId)).thenReturn(loginResp());

        var resp = controller.switchCompany(new SwitchCompanyRequest(companyId));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(authService).switchCompany("u@e.com", companyId);
    }
    */
}
