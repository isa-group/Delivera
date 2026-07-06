package com.delivera.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.delivera.auth.dto.Device;
import com.delivera.auth.model.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken,UUID> {

    Optional<RefreshToken> findById(UUID tokenId);

    @Query("""
        SELECT rt FROM RefreshToken rt
        WHERE rt.id = :id
        AND rt.expiredAt > CURRENT_TIMESTAMP
        AND rt.maxExpiredAt > CURRENT_TIMESTAMP
        AND rt.revoked = false
    """)
    Optional<RefreshToken> findValidById(UUID id);

    
    @Modifying
    @Query("""
        delete from RefreshToken rt
        where rt.expiredAt < :now
        or rt.maxExpiredAt < :now
    """)
    void deleteByExpiredTokens(Instant now);


    
    @Query("""
        SELECT new com.delivera.auth.dto.Device(
            rt.ip,
            rt.userAgent,
            rt.revoked,
            rt.suspicious,
            rt.lastUsed,
            rt.createdAt,
            rt.maxExpiredAt,
            rt.id = :id
        )
        FROM RefreshToken rt
        WHERE rt.credential.userId = :userId
        AND rt.expiredAt > CURRENT_TIMESTAMP
        AND rt.maxExpiredAt > CURRENT_TIMESTAMP
    """)
    List<Device> getDevices(UUID userId, UUID id);

}
