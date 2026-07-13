package com.library.admin.dto;

import com.library.admin.entity.Coupon;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CouponDto {
    private String  id;
    private String  code;
    private Integer discountPercent;
    private boolean isActive;
    private String  createdAt;

    public static CouponDto fromEntity(Coupon c) {
        return CouponDto.builder()
                .id(c.getId().toString())
                .code(c.getCode())
                .discountPercent(c.getDiscountPercent())
                .isActive(c.isActive())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                .build();
    }
}
