package com.delivera.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.delivera.depot.model.OperationalUnit;
import com.delivera.model.LoyalUser;
import com.delivera.org.model.Company;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, unique = true, length = 25)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false)
    private OperationalUnit origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private OperationalUnit destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderPriority priority = OrderPriority.NORMAL;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "tracking_token", length = 64, unique = true)
    private String trackingToken;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;

    @Column(name = "recipient_latitude", precision = 9, scale = 6)
    private BigDecimal recipientLatitude;

    @Column(name = "recipient_longitude", precision = 9, scale = 6)
    private BigDecimal recipientLongitude;

    @Column(name = "current_lat", precision = 9, scale = 6)
    private BigDecimal currentLat;

    @Column(name = "current_lon", precision = 9, scale = 6)
    private BigDecimal currentLon;

    @Column(name = "current_location_at")
    private Instant currentLocationAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyal_user_id")
    private LoyalUser loyalUser;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC")
    private List<OrderEvent> events = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
