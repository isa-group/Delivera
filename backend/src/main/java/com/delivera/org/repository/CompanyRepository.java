package com.delivera.org.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.org.dto.CompanyAdminProjection;
import com.delivera.org.dto.IdNameProjection;
import com.delivera.org.model.Company;

import java.util.List;
import java.util.Set;
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

    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE c.id = :id AND c.organization.id = :organizationId")
    Boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);



    @Query("""
        SELECT new com.delivera.org.dto.IdNameProjection(
            c.id,
            c.name
        )
        FROM Company c 
        WHERE c.id IN(:companyIds)
    """)
   List<IdNameProjection> findNamesById(@Param("companyIds") Set<UUID> companyIds);
   @Query("""
    SELECT new com.delivera.org.dto.IdNameProjection(
        c.id,
        c.name
    )
    FROM Company c 
    WHERE c.organization.id = :organizationId
    """)
   List<IdNameProjection> findNamesByOrgId(@Param("organizationId") UUID organizationId);


   @Query("""
    SELECT new com.delivera.org.dto.CompanyAdminProjection(
        c.id,
        c.name,
        c.organization.name
    )
    FROM Company c 
    """)
    List<CompanyAdminProjection> findCompanyAndOrgNames();
}
