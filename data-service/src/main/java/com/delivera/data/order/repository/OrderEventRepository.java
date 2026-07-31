package com.delivera.data.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delivera.data.order.model.OrderEvent;

import java.util.Set;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    @Modifying
    @Query("DELETE FROM OrderEvent e WHERE e.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);


    
    @Modifying
    @Query("DELETE FROM OrderEvent e WHERE e.order.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM OrderEvent e WHERE e.order.companyId IN :companyIds")
    void deleteByCompanyIds(@Param("companyIds") Set<UUID> companyIds);


    @Modifying
    @Query("DELETE FROM OrderEvent e WHERE e.order.companyId <> :companyId")
    void deleteAllExceptCompanyIds(@Param("companyId") UUID companyId);

    @Modifying
    @Query("DELETE FROM OrderEvent e")
    void deleteAllEvents();
}
