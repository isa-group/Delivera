package com.delivera.data.depot.repository;


import java.util.List;
import java.util.UUID;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.data.depot.model.UnitWorker;

public interface WorkerRepository extends JpaRepository<UnitWorker, UUID>{


    @Modifying
    @Query("""
        DELETE FROM UnitWorker w
        WHERE 
            w.workerId = :workerId
            AND
            w.unit.id = :unitId
            AND 
            w.companyId = :companyId
    """)
    void unassignWorker(
        @Param("workerId") UUID workerId, 
        @Param("unitId") UUID unitId,
        @Param("companyId") UUID companyId
    );

    @Query("""
        SELECT w.workerId 
        FROM UnitWorker w
        WHERE 
            w.unit.id = :unitId
            AND 
            w.companyId = :companyId
    """)
    List<UUID> findWorkersIdByUnitIdAndCompanyId( 
        @Param("unitId") UUID workerId,  
        @Param("companyId") UUID companyId
    );




}
