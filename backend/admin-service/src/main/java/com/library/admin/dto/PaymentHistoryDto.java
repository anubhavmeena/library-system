package com.library.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentHistoryDto {
    private UUID          id;
    private UUID          membershipId;
    private BigDecimal    amount;
    private String        paymentGateway;   // "CASHFREE" | "RAZORPAY" | null
    private String        gatewayOrderId;   // order reference number
    private String        gatewayPaymentId; // UPI / payment reference (null for cash/dev)
    private String        status;           // "PENDING" | "SUCCESS" | "FAILED" | "REFUNDED"
    private LocalDateTime paidAt;
}
