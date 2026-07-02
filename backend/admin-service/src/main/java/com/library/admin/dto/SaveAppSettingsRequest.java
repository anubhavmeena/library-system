package com.library.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class SaveAppSettingsRequest {
    private String wifiName;
    private String wifiPassword;

    @NotNull @Min(0) @Max(90)
    private Integer graceDays;

    @NotNull @DecimalMin("0.00") @DecimalMax("500.00")
    private BigDecimal convenienceFee;

    @NotNull @DecimalMin("0.00")
    private BigDecimal waterTankerRate;
}
