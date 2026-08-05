package com.delivera.data.org.model;

import java.util.UUID;



import com.delivera.data.order.model.OrderPriority;
import com.delivera.data.org.dto.CompanySettingsDTO;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class CompanySettings {

    /**
     * Same Id as CompanyId
     */
    @Id
    @Column(name = "company_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", length = 10)
    private OrderPriority defaultPriority;

    @Column(name = "default_priority_locked", nullable = false)
    private boolean defaultPriorityLocked = false;


    public CompanySettingsDTO dto() {
        CompanySettingsDTO dto = new CompanySettingsDTO();
        dto.setCompanyId(id);
        dto.setDefaultPriority(defaultPriority);
        dto.setDefaultPriorityLocked(defaultPriorityLocked);
        return dto;        
    }
}
