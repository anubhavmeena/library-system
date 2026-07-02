package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppSettingsDto {
    private String wifiName;
    private String wifiPassword;
    private Integer graceDays;
    private BigDecimal convenienceFee;
    private BigDecimal waterTankerRate;
    private String updatedAt;
}
