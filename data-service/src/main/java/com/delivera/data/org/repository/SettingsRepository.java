package com.delivera.data.org.repository;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.data.org.model.CompanySettings;

public interface SettingsRepository extends JpaRepository<CompanySettings, UUID> {

    @Modifying
    @Query("""
    DELETE FROM CompanySettings s 
    WHERE s.id IN :companyIds        
    """)
    void deleteByCompanyIds(@Param("companyIds") Set<UUID> companyIds);

    @Modifying
    @Query("""
    DELETE FROM CompanySettings s 
    WHERE s.id <> :companyId        
    """)
    void deleteAllExceptCompanyId(@Param("companyId") UUID companyId);

}
