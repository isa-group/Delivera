package com.delivera.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loyal_users")
public class LoyalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToMany(mappedBy = "loyalUser", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LoyalUserCompany> companyLinks = new ArrayList<>();

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Crea (si no existe) y devuelve el vínculo per-empresa de este fidelizado con {@code company}. */
    public LoyalUserCompany linkFor(Company company) {
        return findLink(company.getId()).orElseGet(() -> {
            LoyalUserCompany link = new LoyalUserCompany();
            link.setLoyalUser(this);
            link.setCompany(company);
            // El id se deriva via @MapsId al persistir.
            companyLinks.add(link);
            return link;
        });
    }

    public Optional<LoyalUserCompany> findLink(UUID companyId) {
        return companyLinks.stream()
                .filter(l -> l.getCompany() != null && companyId.equals(l.getCompany().getId()))
                .findFirst();
    }

    public boolean unlinkFrom(UUID companyId) {
        return companyLinks.removeIf(l -> l.getCompany() != null && companyId.equals(l.getCompany().getId()));
    }
}
