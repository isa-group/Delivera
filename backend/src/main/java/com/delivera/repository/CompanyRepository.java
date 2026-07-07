package com.delivera.repository;

import com.delivera.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findByOrganizationId(UUID organizationId);
    List<Company> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    @Modifying
    @Query("DELETE FROM Company c WHERE c.organization.id = :orgId")
    void deleteByOrganizationId(@Param("orgId") UUID orgId);

    @Modifying
    @Query("DELETE FROM Company c WHERE c.id <> :keepId")
    void deleteAllExcept(@Param("keepId") UUID keepId);

    @Query("SELECT COUNT(c) FROM Company c WHERE c.organization.handle <> 'delivera'")
    long countTenants();
}
