package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "membership_plans")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Plan {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false) private String name;

    @Column(name = "plan_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanType planType;

    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal price;
    @Column(name = "duration_days")                      private Integer    durationDays;
    @Column(name = "is_active")                          private Boolean    isActive = true;

    public enum PlanType { HALF_DAY, FULL_DAY }
}