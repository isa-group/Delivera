package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.order.model.Order;
import com.delivera.order.model.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query(value = "SELECT nextval('order_ref_seq')", nativeQuery = true)
    Long nextReferenceSeq();

    List<Order> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.origin origin LEFT JOIN FETCH origin.company " +
           "LEFT JOIN FETCH o.destination dest LEFT JOIN FETCH dest.company dc " +
           "LEFT JOIN FETCH o.loyalUser " +
           "WHERE o.company.id = :companyId " +
           "OR (dest IS NOT NULL AND dc.id = :companyId AND o.company.id <> :companyId) " +
           "ORDER BY o.createdAt DESC")
    List<Order> findSentOrReceivedByCompanyId(@Param("companyId") UUID companyId);

    Optional<Order> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.origin origin LEFT JOIN FETCH origin.company " +
           "LEFT JOIN FETCH o.destination dest LEFT JOIN FETCH dest.company dc " +
           "LEFT JOIN FETCH o.loyalUser lu LEFT JOIN FETCH lu.user " +
           "LEFT JOIN FETCH o.events " +
           "WHERE o.id = :id AND (o.company.id = :companyId OR (dest IS NOT NULL AND dc.id = :companyId))")
    Optional<Order> findByIdForCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<Order> findByTrackingToken(String trackingToken);

    Optional<Order> findByReference(String reference);

    List<Order> findByLoyalUserIdOrderByCreatedAtDesc(UUID loyalUserId);

    List<Order> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    long countByLoyalUserId(UUID loyalUserId);

    boolean existsByCompanyIdAndStatusIn(UUID companyId, List<OrderStatus> statuses);

    List<Order> findByCompanyId(UUID companyId);

    @Modifying
    @Query("DELETE FROM OrderEvent e WHERE e.order.company.id = :companyId")
    void deleteEventsByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM Order o WHERE o.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    long countByCompanyIdAndCreatedAtAfter(UUID companyId, Instant after);

    long countByCompanyIdAndStatusAndCreatedAtAfter(UUID companyId, OrderStatus status, Instant after);

    long countByCompanyIdAndStatusNotIn(UUID companyId, java.util.Collection<OrderStatus> statuses);

    @Query("SELECT CAST(o.createdAt AS LocalDate) AS day, COUNT(o) " +
           "FROM Order o WHERE o.company.id = :companyId AND o.createdAt > :after " +
           "GROUP BY CAST(o.createdAt AS LocalDate) ORDER BY day")
    List<Object[]> countByDayForCompany(@Param("companyId") UUID companyId, @Param("after") Instant after);

    @Query("SELECT u.id, u.name, u.type, COUNT(o) " +
           "FROM Order o JOIN o.origin u " +
           "WHERE o.company.id = :companyId AND o.createdAt > :after " +
           "GROUP BY u.id, u.name, u.type " +
           "ORDER BY COUNT(o) DESC")
    List<Object[]> countByOriginUnitForCompany(@Param("companyId") UUID companyId, @Param("after") Instant after);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.company.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") UUID orgId);

    long countByCreatedAtAfter(Instant after);

    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.loyalUser.user.email = :email")
    Optional<Order> findByIdForLoyalUser(@Param("id") UUID id, @Param("email") String email);
}
