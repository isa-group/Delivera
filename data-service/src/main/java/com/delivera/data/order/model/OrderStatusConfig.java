package com.delivera.data.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_status_config")
public class OrderStatusConfig {

    @Id
    private String status;

    @Column(name = "ui_severity", nullable = false, length = 20)
    private String uiSeverity;

    @Column(name = "allowed_transitions", nullable = false, length = 500)
    private String allowedTransitions;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public List<String> getAllowedTransitionsList() {
        if (allowedTransitions == null || allowedTransitions.isBlank()) return List.of();
        return List.of(allowedTransitions.split(","));
    }
}
