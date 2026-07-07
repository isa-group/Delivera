package com.delivera.repository;

import com.delivera.model.LoyalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyalUserRepository extends JpaRepository<LoyalUser, UUID> {

    @Query("SELECT lu FROM LoyalUser lu JOIN lu.companyLinks l " +
           "WHERE l.company.id = :companyId ORDER BY l.createdAt DESC")
    List<LoyalUser> findByCompanyIdOrderByLinkCreatedAtDesc(@Param("companyId") UUID companyId);

    @Query("SELECT lu, (SELECT COUNT(o) FROM Order o WHERE o.loyalUser = lu AND o.company.id = :companyId) " +
           "FROM LoyalUser lu LEFT JOIN FETCH lu.user JOIN lu.companyLinks l " +
           "WHERE l.company.id = :companyId ORDER BY l.createdAt DESC")
    List<Object[]> findWithOrderCountByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT lu FROM LoyalUser lu JOIN lu.companyLinks l " +
           "WHERE l.company.id = :companyId AND lu.email = :email")
    Optional<LoyalUser> findByCompanyIdAndEmail(@Param("companyId") UUID companyId, @Param("email") String email);

    @Query("SELECT lu FROM LoyalUser lu JOIN lu.companyLinks l " +
           "WHERE lu.id = :id AND l.company.id = :companyId")
    Optional<LoyalUser> findByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("SELECT lu FROM LoyalUser lu WHERE lu.email = :email")
    List<LoyalUser> findByEmail(@Param("email") String email);

    @Query("SELECT COUNT(DISTINCT lu) FROM LoyalUser lu JOIN lu.companyLinks l WHERE l.company.id = :companyId")
    long countByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COUNT(DISTINCT lu) FROM LoyalUser lu JOIN lu.companyLinks l " +
           "WHERE l.company.id = :companyId AND l.createdAt > :createdAtAfter")
    long countByCompanyIdAndLinkCreatedAfter(@Param("companyId") UUID companyId,
                                             @Param("createdAtAfter") Instant createdAtAfter);

    @Query("SELECT lu FROM LoyalUser lu WHERE lu.user.id = :userId")
    Optional<LoyalUser> findByUserId(@Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM LoyalUser lu")
    void deleteAllLoyalUsers();
}
