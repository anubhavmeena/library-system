package com.library.membership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidateCouponRequest {
    @NotBlank
    private String code;
}
