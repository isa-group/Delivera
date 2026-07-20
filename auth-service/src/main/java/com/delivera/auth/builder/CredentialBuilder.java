package com.delivera.auth.builder;

import java.util.UUID;

import com.delivera.auth.model.Credential;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CredentialBuilder {

    private Credential credential;

    public static CredentialBuilder of() {
        CredentialBuilder credentialBuilder = new CredentialBuilder();
        credentialBuilder.setCredential(new Credential());
        return credentialBuilder;
    }

    public CredentialBuilder userId(UUID userId) {
        this.credential.setUserId(userId);
        return this;
    }

    public CredentialBuilder email(String email) {
        this.credential.setEmail(email);
        return this;
    }

    public CredentialBuilder username(String username) {
        this.credential.setUsername(username);
        return this;
    }

    public CredentialBuilder passwordHash(String passwordHash) {
        this.credential.setPasswordHash(passwordHash);
        return this;
    }

    public CredentialBuilder tokenVersion(int tokenVersion) {
        this.credential.setTokenVersion(tokenVersion);;
        return this;
    }

    public Credential build() {
        return this.credential;
    }

}
