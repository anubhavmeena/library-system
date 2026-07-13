package com.library.membership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// Read-only sibling of admin-service's Coupon entity, mapping only the
// columns this service needs to validate/apply a code at checkout —
// admin-service owns writes to the `coupons` table; this service only reads.
@Entity
@Table(name = "coupons")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Coupon {

    @Id
    private UUID id;

    private String code;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "is_active")
    private boolean isActive;
}
