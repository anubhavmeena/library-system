package com.library.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCouponRequest {
    // Blank/absent auto-generates an 8-character code.
    private String code;

    @NotNull @Min(1) @Max(100)
    private Integer discountPercent;
}
