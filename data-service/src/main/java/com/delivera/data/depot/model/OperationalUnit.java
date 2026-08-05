package com.delivera.data.depot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.delivera.data.order.model.OrderPriority;



@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operational_units")
public class OperationalUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType type;

    @Column(length = 500)
    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }


    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", length = 10)
    private OrderPriority defaultPriority;

}
