package com.delivera.client.config.properties;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.delivera.client.exception.CompanyContextException;
import com.delivera.client.model.UserPrincipal;


public class SecurityUtils {

    
    private UserPrincipal getPrincipal() {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null) {
                throw new CompanyContextException();
            }


            if (auth.getPrincipal() instanceof UserPrincipal principal) {
                return principal;
            }


            if (auth instanceof JwtAuthenticationToken jwtAuth) {

                var jwt = jwtAuth.getToken();
                Number ver = jwt.getClaim("ver");
                Integer version = ver != null ? ver.intValue() : null;
                return new UserPrincipal(
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getClaim("companyId")),
                    jwt.getClaim("email"),
                    jwt.getClaim("role"),
                    version
                );
            }

            throw new CompanyContextException();


    }

    public UUID getCurrentUserId() {
        return getPrincipal().getUserId();
    }

    public UUID getCurrentCompanyId() {
        return getPrincipal().getCompanyId();
    }

    public String getCurrentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) return null;

        return getPrincipal().getEmail();
    }

    public String getCurrentRole() {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) return null;

        return auth.getAuthorities().stream()
        .map(a -> a.getAuthority().replace("ROLE_", ""))
        .findFirst()
        .orElse(null);
    }

    public Integer getTokenVersion() {
        return getPrincipal().getTokenVersion();
    }

}