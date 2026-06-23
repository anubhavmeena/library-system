package com.library.admin.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAmountBreakdownDto {
    private BigDecimal amount;
    private long       count;
}
