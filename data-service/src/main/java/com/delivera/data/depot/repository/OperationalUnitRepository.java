package com.delivera.data.depot.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.data.common.dto.IdNameProjection;
import com.delivera.data.depot.dto.B2BUnitResponse;
import com.delivera.data.depot.dto.UnitAdminSummary;
import com.delivera.data.depot.model.OperationalUnit;
/*import com.delivera.data.worker.model.Worker;*/

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationalUnitRepository extends JpaRepository<OperationalUnit, UUID> {

    List<OperationalUnit> findAllByCompanyId(UUID companyId);

    Optional<OperationalUnit> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndName(UUID companyId, String name);

    boolean existsByCompanyIdAndNameAndIdNot(UUID companyId, String name, UUID id);


    
    @Query("""
      SELECT DISTINCT w.unit
      FROM UnitWorker w
      WHERE
          w.unit.companyId = :companyId
          AND 
          w.userId = :userId
    """)
    List<OperationalUnit> findAllByCompanyIdAndUserId(UUID companyId, UUID userId);

   /*  @Query("SELECT u FROM OperationalUnit u LEFT JOIN FETCH u.workers WHERE u.id = :id AND u.company.id = :companyId")
    Optional<OperationalUnit> findByIdAndCompanyIdWithWorkers(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("SELECT u FROM OperationalUnit u WHERE u.company.organization.id = " +
           "(SELECT c.organization.id FROM Company c WHERE c.id = :companyId) " +
           "AND u.company.id <> :companyId")
    List<OperationalUnit> findExternalByOrganization(@Param("companyId") UUID companyId);

    @Query("SELECT u FROM OperationalUnit u JOIN FETCH u.company c JOIN FETCH c.organization WHERE u.company.id <> :companyId")
    List<OperationalUnit> findAllExternalUnits(@Param("companyId") UUID companyId);

    @Query("SELECT u FROM OperationalUnit u JOIN FETCH u.company c JOIN FETCH c.organization WHERE u.company.id <> :companyId")
    List<OperationalUnit> findAllExternalUnits(@Param("companyId") UUID companyId);

    @Query("SELECT u FROM OperationalUnit u WHERE u.id = :id AND u.company.organization.id = :orgId")
    Optional<OperationalUnit> findByIdAndOrganizationId(@Param("id") UUID id, @Param("orgId") UUID orgId);
*/
    
    @Query("""
        SELECT new com.delivera.data.depot.dto.B2BUnitResponse(
            u.id,
            u.name,
            u.type,
            u.companyId,
            u.orgId
        )
        FROM OperationalUnit u
        WHERE 
          u.companyId <> :companyId
          AND
          u.companyId = :externalCompanyId
    """)
    List<B2BUnitResponse> findAllExternalUnits(
        @Param("companyId") UUID companyId,
        @Param("externalCompanyId") UUID externalCompanyId
    );

    long countByCompanyId(UUID companyId);
    /*
    @Query("SELECT u FROM OperationalUnit u WHERE u.company.id = :companyId AND NOT EXISTS (SELECT o FROM Order o WHERE o.origin.id = u.id OR o.destination.id = u.id) ORDER BY u.createdAt DESC")
    List<OperationalUnit> findByCompanyIdWithNoOrdersOrderByCreatedAtDesc(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM OperationalUnit u WHERE u.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);
     */
    @Modifying
    @Query("DELETE FROM OperationalUnit u")
    void deleteAllUnits();


    @Modifying
    @Query("""
        DELETE 
        FROM OperationalUnit u 
        WHERE u.companyId = :companyId
    """)
    void deleteAllFromCompany(@Param("companyId") UUID companyId);

    @Query("""
    SELECT new com.delivera.data.common.dto.IdNameProjection(
        u.id,
        u.name
    )
    FROM OperationalUnit u 
    WHERE u.companyId = :companyId
    """)
   List<IdNameProjection> findNamesByCompanyId(@Param("companyId") UUID companyId);



   @Query("""
    SELECT new com.delivera.data.depot.dto.UnitAdminSummary(
        u.id,
        u.name,
        u.type,
        u.latitude,
        u.longitude,
        u.companyId,
        u.orgId

    )
    FROM OperationalUnit u
    WHERE 
        u.latitude IS NOT NULL
        AND
        u.longitude IS NOT NULL
    """)
   List<UnitAdminSummary> findAdminSummaries();
}
