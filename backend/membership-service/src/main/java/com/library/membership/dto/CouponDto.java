package com.library.membership.dto;

import com.library.membership.entity.Coupon;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CouponDto {
    private String  code;
    private Integer discountPercent;

    public static CouponDto fromEntity(Coupon c) {
        return CouponDto.builder()
                .code(c.getCode())
                .discountPercent(c.getDiscountPercent())
                .build();
    }
}
