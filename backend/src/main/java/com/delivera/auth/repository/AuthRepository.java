package com.delivera.auth.repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.worker.model.Worker;


public interface AuthRepository  extends JpaRepository<Worker, UUID> {

    
    @Query("""
    SELECT new com.delivera.auth.dto.DeliveraOrgContext(
        c.id,
        w.role,
        c.name,
        o.handle,
        o.name
    )
    FROM Worker w
    JOIN w.company c
    JOIN c.organization o
    JOIN w.user u
    WHERE u.id = :userId
    ORDER BY w.createdAt ASC
    """)
    List<DeliveraOrgContext> findOrgContextByUserId(
        @Param("userId") UUID userId,
        Pageable pageable
    );

    @Query("""
    SELECT new com.delivera.auth.dto.DeliveraOrgContext(
        c.id,
        w.role,
        c.name,
        o.handle,
        o.name
    )
    FROM Worker w
    JOIN w.company c
    JOIN c.organization o
    JOIN w.user u
    WHERE u.id = :userId AND c.id = :companyId
    """)
    Optional<DeliveraOrgContext> findOrgContextByUserIdAndCompanyId(
        @Param("userId") UUID userId,@Param("companyId") UUID companyId
    );
    

}
