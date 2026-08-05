package com.delivera.data.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_priority_config")
public class OrderPriorityConfig {

    @Id
    private String priority;

    @Column(name = "ui_severity", nullable = false, length = 20)
    private String uiSeverity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
