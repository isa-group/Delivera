package com.delivera.repository;

import com.delivera.model.LoyalUserCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LoyalUserCompanyRepository extends JpaRepository<LoyalUserCompany, LoyalUserCompany.Id> {

    @Modifying
    @Query("DELETE FROM LoyalUserCompany luc WHERE luc.id.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM LoyalUserCompany luc")
    void deleteAllLinks();
}
