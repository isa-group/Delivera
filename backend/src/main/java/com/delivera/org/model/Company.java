package com.delivera.org.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import com.delivera.model.ActivityType;
import com.delivera.model.SubscriptionPlan;
import com.delivera.order.model.OrderPriority;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_code", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "logo_data", columnDefinition = "TEXT")
    private String logoData;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", length = 10)
    private OrderPriority defaultPriority;

    @Column(name = "default_priority_locked", nullable = false)
    private boolean defaultPriorityLocked = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
