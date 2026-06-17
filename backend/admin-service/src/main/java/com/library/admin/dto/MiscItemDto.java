package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class MiscItemDto {
    private String     description;
    private BigDecimal amount;
}
