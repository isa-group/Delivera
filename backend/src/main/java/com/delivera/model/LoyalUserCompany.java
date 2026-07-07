package com.delivera.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loyal_user_companies")
public class LoyalUserCompany {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("loyalUserId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyal_user_id")
    private LoyalUser loyalUser;

    @MapsId("companyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(length = 255)
    private String name;

    @Column(length = 20)
    private String phone;

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

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "loyal_user_id") private UUID loyalUserId;
        @Column(name = "company_id")    private UUID companyId;

        public Id(UUID loyalUserId, UUID companyId) {
            this.loyalUserId = loyalUserId;
            this.companyId = companyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(loyalUserId, other.loyalUserId)
                && Objects.equals(companyId, other.companyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(loyalUserId, companyId);
        }
    }
}
