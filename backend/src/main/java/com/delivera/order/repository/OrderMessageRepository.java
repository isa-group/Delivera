package com.delivera.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.order.model.OrderMessage;

import java.util.List;
import java.util.UUID;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, UUID> {
    List<OrderMessage> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Modifying
    @Query("DELETE FROM OrderMessage m WHERE m.order.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM OrderMessage m WHERE m.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);

    @Modifying
    @Query("DELETE FROM OrderMessage m WHERE m.sender.id = :userId")
    void deleteBySenderId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM OrderMessage m WHERE m.order.origin.company.id = :companyId")
    void deleteByOriginCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM OrderMessage m")
    void deleteAllMessages();
}
