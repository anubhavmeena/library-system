package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "monthly_expenses", uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MonthlyExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(name = "water_tanker_qty", nullable = false)
    private int waterTankerQty;

    @Column(name = "water_tanker_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal waterTankerPrice;

    @Column(name = "electricity_bill", nullable = false, precision = 10, scale = 2)
    private BigDecimal electricityBill;

    @Column(name = "internet_bill", nullable = false, precision = 10, scale = 2)
    private BigDecimal internetBill;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal miscellaneous;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
