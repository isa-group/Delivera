package com.delivera.repository;

import com.delivera.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ApiKey> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    @Modifying
    @Query("DELETE FROM ApiKey a WHERE a.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM ApiKey a WHERE a.company.id <> :keepCompanyId")
    void deleteAllExceptCompany(@Param("keepCompanyId") UUID keepCompanyId);
}
