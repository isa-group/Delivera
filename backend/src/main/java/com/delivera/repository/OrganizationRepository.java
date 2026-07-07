package com.delivera.repository;

import com.delivera.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsByHandle(String handle);
    boolean existsByHandleAndIdNot(String handle, UUID id);

    @Query("SELECT o.id, o.name, o.handle, o.createdAt, " +
           "(SELECT COUNT(c) FROM Company c WHERE c.organization = o), " +
           "(SELECT COUNT(w) FROM Worker w WHERE w.company.organization = o), " +
           "(SELECT COUNT(ord) FROM Order ord WHERE ord.company.organization = o) " +
           "FROM Organization o WHERE o.handle <> 'delivera' ORDER BY o.createdAt DESC")
    List<Object[]> findAllSummaries();

    @Query("SELECT COUNT(o) FROM Organization o WHERE o.handle <> 'delivera'")
    long countTenants();

    Optional<Organization> findByHandle(String handle);

    @Modifying
    @Query("DELETE FROM Organization o WHERE o.id <> :keepId")
    void deleteAllExcept(@Param("keepId") UUID keepId);
}
