package com.delivera.auth.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.delivera.auth.model.Credential;

@Repository
public interface CredentialRepository extends CrudRepository<Credential,UUID> {

    Optional<Credential> findByEmailOrUsername(String email, String username);

    Optional<Credential> findByEmail(String email);

    Optional<Credential> findByUsername(String username);

    Optional<Credential> findByUserId(UUID userId);

    
    Optional<Credential> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);


    @Modifying
    @Query("""
    DELETE FROM Credential c
    WHERE c.userId IN :userIds
    """)
    void deleteUsers(@Param("userIds") Set<UUID> userIds);

    @Modifying
    @Query("""
    DELETE FROM Credential c
    WHERE c.userId <> :userId
    """)
    void deleteAllExceptUserId(@Param("userId") UUID userId);



}
