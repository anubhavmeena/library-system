package com.library.admin.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyPaymentDto {
    private String     studentName;
    private String     studentMobile;
    private BigDecimal amount;
    private String     paymentGateway;
    private String     referenceId;    // gatewayPaymentId if present, else gatewayOrderId
    private String     paidAt;
}
