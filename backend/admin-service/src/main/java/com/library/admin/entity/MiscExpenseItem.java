package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "misc_expense_items")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MiscExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "monthly_expense_id", nullable = false)
    private UUID monthlyExpenseId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
