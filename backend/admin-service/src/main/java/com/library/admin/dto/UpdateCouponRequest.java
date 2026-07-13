package com.library.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateCouponRequest {
    @Min(1) @Max(100)
    private Integer discountPercent;

    private Boolean isActive;
}
