package com.delivera.data.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.delivera.data.vehicle.model.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByCompanyId(UUID companyId);

    Optional<Vehicle> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndPlate(UUID companyId, String plate);

    boolean existsByCompanyIdAndPlateAndIdNot(UUID companyId, String plate, UUID id);

    long countByCompanyId(UUID companyId);

    void deleteByCompanyId(UUID companyId);

    @Modifying
    @Query("""
        DELETE 
        FROM Vehicle v 
        WHERE v.companyId = :companyId
    """)
    void deleteAllFromCompany(@Param("companyId") UUID companyId);
}
