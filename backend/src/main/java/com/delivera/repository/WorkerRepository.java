package com.delivera.repository;

import com.delivera.model.Worker;
import com.delivera.model.WorkerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    @Query("SELECT w FROM Worker w JOIN FETCH w.company c JOIN c.organization o JOIN w.user u WHERE u.email = :email AND o.handle = :handle ORDER BY w.createdAt ASC")
    List<Worker> findByUserEmailAndOrganizationHandle(@Param("email") String email, @Param("handle") String handle);

    boolean existsByUserEmail(String email);

    @Query("SELECT w FROM Worker w JOIN FETCH w.company c JOIN c.organization o JOIN w.user u WHERE u.email = :email AND c.id = :companyId")
    Optional<Worker> findByUserEmailAndCompanyId(@Param("email") String email, @Param("companyId") UUID companyId);

    @Query("SELECT w FROM Worker w JOIN FETCH w.company c JOIN c.organization o JOIN w.user u WHERE u.email = :email AND o.id = :orgId")
    List<Worker> findByUserEmailAndOrgId(@Param("email") String email, @Param("orgId") UUID orgId);

    List<Worker> findByCompanyId(UUID companyId);

    List<Worker> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    List<Worker> findByCompanyIdAndRoleNotOrderByCreatedAtAsc(UUID companyId, WorkerRole role);

    Optional<Worker> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndRole(UUID companyId, WorkerRole role);


    @Query("SELECT w FROM Worker w JOIN FETCH w.company c JOIN c.organization o JOIN w.user u WHERE u.email = :email ORDER BY w.createdAt ASC")
    List<Worker> findByUserEmailOrderByCreatedAtAsc(@Param("email") String email);

    long countByCompanyId(UUID companyId);

    long countByUser_Id(UUID userId);

    @Query("SELECT COUNT(w) FROM Worker w WHERE w.company.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") UUID orgId);

    @Modifying
    @Query("DELETE FROM Worker w WHERE w.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    boolean existsByUser_IdAndRole(UUID userId, WorkerRole role);

    @Query("SELECT w FROM Worker w WHERE w.user.id = :userId")
    List<Worker> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Worker w WHERE w.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Worker w WHERE w.role <> :role")
    void deleteAllExceptRole(@Param("role") WorkerRole role);

    @Modifying
    @Query(value = "DELETE FROM unit_workers WHERE worker_id IN (SELECT id FROM workers WHERE company_id = :companyId)", nativeQuery = true)
    void deleteUnitWorkersByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query(value = "DELETE FROM unit_workers WHERE worker_id = :workerId", nativeQuery = true)
    void deleteUnitWorkersByWorkerId(@Param("workerId") UUID workerId);

    @Modifying
    @Query(value = "DELETE FROM unit_workers WHERE worker_id IN (SELECT id FROM workers WHERE user_id = :userId)", nativeQuery = true)
    void deleteUnitWorkersByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM unit_workers WHERE worker_id NOT IN (SELECT id FROM workers WHERE role = 'GLOBAL_ADMIN')", nativeQuery = true)
    void deleteAllUnitWorkersExceptGlobalAdmin();

    @Modifying
    @Query(value = "DELETE FROM unit_workers", nativeQuery = true)
    void deleteAllUnitWorkers();
}
