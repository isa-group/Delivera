package com.delivera.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;


import com.delivera.auth.model.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken,UUID> {


    Optional<RefreshToken> findById(UUID tokenId);

    @Query("""
        SELECT rt FROM RefreshToken rt
        WHERE rt.id = :id
        AND rt.expiredAt > CURRENT_TIMESTAMP
        AND rt.revoked = false
    """)
    Optional<RefreshToken> findValidById(UUID id);

}
