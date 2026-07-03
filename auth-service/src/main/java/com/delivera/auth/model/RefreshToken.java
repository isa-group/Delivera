package com.delivera.auth.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class RefreshToken {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "credential_id")
    private Credential credential;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "max_expired_at")
    private Instant maxExpiredAt;

    private Boolean revoked = false;

    private Boolean suspicious = false;

    @Size(max = 1000)
    private String userAgent;

    private String device;

    private String ip;

    private Instant lastUsed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

}
