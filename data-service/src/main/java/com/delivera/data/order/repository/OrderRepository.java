package com.delivera.data.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.data.common.dto.IdCountProjection;
import com.delivera.data.order.dto.OrderAdminSummary;
import com.delivera.data.order.model.Order;
import com.delivera.data.order.model.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

       @Query(value = "SELECT nextval('order_ref_seq')", nativeQuery = true)
       Long nextReferenceSeq();



       @Query("""
       SELECT new com.delivera.data.common.dto.IdCountProjection(
              u.orgId,
              COUNT(o)
       )
       FROM Order o
       JOIN o.origin u
       GROUP BY u.orgId
       """)
       List<IdCountProjection> countByOrganization();

       @Query("""
       SELECT new com.delivera.data.common.dto.IdCountProjection(
              o.companyId,
              COUNT(o)
       )
       FROM Order o
       GROUP BY o.companyId
       """)
       List<IdCountProjection> countByCompany();

       List<Order> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

       @Query("""
       SELECT DISTINCT o
       FROM Order o
       LEFT JOIN FETCH o.origin
       LEFT JOIN FETCH o.destination
       WHERE
              o.companyId = :companyId
              OR (
                     o.destination IS NOT NULL
                     AND 
                     o.destination.companyId = :companyId
              )
       ORDER BY o.createdAt DESC
       """)
       List<Order> findSentOrReceivedByCompanyId(@Param("companyId") UUID companyId);

       Optional<Order> findByIdAndCompanyId(UUID id, UUID companyId);

       @Query("""
       SELECT DISTINCT o
       FROM Order o
       LEFT JOIN FETCH o.origin
       LEFT JOIN FETCH o.destination
       LEFT JOIN FETCH o.events
       WHERE
              o.id = :id
              AND (
                     o.companyId = :companyId
                     OR (
                            o.destination IS NOT NULL
                            AND 
                            o.destination.companyId = :companyId
                     )
              )
       """)
       Optional<Order> findByIdForCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

       Optional<Order> findByTrackingToken(String trackingToken);

       Optional<Order> findByReference(String reference);

       List<Order> findByLoyalUserIdOrderByCreatedAtDesc(UUID loyalUserId);

       List<Order> findByLoyalUserIdAndCompanyIdOrderByCreatedAtDesc(UUID loyalUserId, UUID companyId);

       List<Order> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

       
       Optional<Order> findByIdAndRecipientEmail(UUID id,String recipientEmail);

       long countByLoyalUserId(UUID loyalUserId);

       boolean existsByCompanyIdAndStatusIn(UUID companyId, List<OrderStatus> statuses);

       List<Order> findByCompanyId(UUID companyId);

       @Modifying
       @Query("DELETE FROM OrderEvent e WHERE e.order.companyId = :companyId")
       void deleteEventsByCompanyId(@Param("companyId") UUID companyId);

       @Modifying
       @Query("DELETE FROM Order o WHERE o.companyId = :companyId")
       void deleteByCompanyId(@Param("companyId") UUID companyId);

       long countByCompanyIdAndCreatedAtAfter(UUID companyId, Instant after);

       long countByCompanyIdAndStatusAndCreatedAtAfter(UUID companyId, OrderStatus status, Instant after);

       long countByCompanyIdAndStatusNotIn(UUID companyId, java.util.Collection<OrderStatus> statuses);

       @Query("SELECT CAST(o.createdAt AS LocalDate) AS day, COUNT(o) " +
              "FROM Order o WHERE o.companyId = :companyId AND o.createdAt > :after " +
              "GROUP BY CAST(o.createdAt AS LocalDate) ORDER BY day")
       List<Object[]> countByDayForCompany(@Param("companyId") UUID companyId, @Param("after") Instant after);

       @Query("SELECT u.id, u.name, u.type, COUNT(o) " +
              "FROM Order o JOIN o.origin u " +
              "WHERE o.companyId = :companyId AND o.createdAt > :after " +
              "GROUP BY u.id, u.name, u.type " +
              "ORDER BY COUNT(o) DESC")
       List<Object[]> countByOriginUnitForCompany(@Param("companyId") UUID companyId, @Param("after") Instant after);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.origin.orgId = :orgId")
       long countByOrganizationId(@Param("orgId") UUID orgId);

       long countByCreatedAtAfter(Instant after);

       long countByStatusAndCreatedAtAfter(OrderStatus status, Instant after);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses AND o.createdAt > :after")
       long countByStatusInAndCreatedAtAfter(@Param("statuses") List<OrderStatus> statuses, @Param("after") Instant after);

       @Query("SELECT CAST(o.createdAt AS LocalDate) AS day, COUNT(o) " +
              "FROM Order o WHERE o.createdAt > :after " +
              "GROUP BY CAST(o.createdAt AS LocalDate) ORDER BY day")
       List<Object[]> countByDayGlobal(@Param("after") Instant after);

       @Query("SELECT o FROM Order o WHERE o.id = :id AND o.recipientEmail = :email")
       Optional<Order> findByIdForLoyalUser(@Param("id") UUID id, @Param("email") String email);

       @Modifying
       @Query("DELETE FROM OrderEvent e WHERE e.order.id = :orderId")
       void deleteEventsByOrderId(@Param("orderId") UUID orderId);

       // TODO: TERMINAR DE VER
       @Query("""
       SELECT new com.delivera.data.common.dto.IdCountProjection(
              o.companyId,
              COUNT(o)
       )
       FROM Order o 
       WHERE o.createdAt > :after 
       GROUP BY o.companyId
       ORDER BY COUNT(o) DESC
       """)
       List<IdCountProjection> rankCompaniesByOrderCount(@Param("after") Instant after);

       @Modifying
       @Query("DELETE FROM OrderEvent e WHERE e.order.origin.companyId = :companyId")
       void deleteEventsByOriginCompanyId(@Param("companyId") UUID companyId);

       @Modifying
       @Query("DELETE FROM Order o WHERE o.origin.companyId = :companyId")
       void deleteByOriginCompanyId(@Param("companyId") UUID companyId);

       @Modifying
       @Query("UPDATE Order o SET o.destination = null WHERE o.destination IS NOT NULL AND o.destination.companyId = :companyId")
       void nullifyDestinationByCompanyId(@Param("companyId") UUID companyId);



     
       @Modifying
       @Query("DELETE FROM Order o")
       void deleteAllOrders();


       @Query("""
       SELECT o.recipientEmail
       FROM Order o
       WHERE 
              o.trackingToken = :token
              AND
              o.claimed = false 
                     
       """)
       Optional<String> findNotClaimedByToken(@Param("token") String token);


       @Query("""
       SELECT DISTINCT o
       FROM UnitWorker w
       JOIN Order o ON
              (o.origin.id = w.unit.id 
              OR 
              o.destination.id = w.unit.id)
       LEFT JOIN FETCH o.origin
       LEFT JOIN FETCH o.destination
       WHERE 
              w.companyId = :companyId
              AND
              w.userId = :userId 
       """)
       List<Order> findSentOrReceivedByCompanyIdAndUserId(
              @Param("companyId") UUID companyId,
              @Param("userId") UUID userId
       );


       @Query("""
       SELECT  new com.delivera.data.order.dto.OrderAdminSummary(
              o.id,
              o.reference,
              o.status,
              o.orderType,
              o.companyId,
              o.createdAt
       )
       FROM Order o
       """)
       List<OrderAdminSummary> findSummaryOfAll();
}
