package com.delivera.config;



import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.exception.CompanyContextException;
import com.delivera.client.model.UserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    private final SecurityUtils securityUtils = new SecurityUtils();
    private final UUID companyId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentCompanyId_nullAuth_throws() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> securityUtils.getCurrentCompanyId())
                .isInstanceOf(CompanyContextException.class);
    }

    @Test
    void getCurrentEmail_withAuth_returnsEmail() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(buildPrincipal(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityUtils.getCurrentEmail()).isEqualTo("user@test.com");
    }

    @Test
    void getCurrentRole_returnsStrippedAuthority_andCompanyId() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            buildPrincipal(), null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
        auth.setDetails(companyId);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityUtils.getCurrentRole()).isEqualTo("COMPANY_ADMIN");
        assertThat(securityUtils.getCurrentCompanyId()).isEqualTo(companyId);
    }

    @Test
    void getCurrentRole_nullAuth_returnsNull() {
        assertThat(securityUtils.getCurrentRole()).isNull();
        assertThat(securityUtils.getCurrentEmail()).isNull();
    }

    private UserPrincipal buildPrincipal() {
        return new UserPrincipal(
                UUID.randomUUID(),
                companyId,
                null,
                "user@test.com",
                "COMPANY_ADMIN",
                0
        );
    }

}
