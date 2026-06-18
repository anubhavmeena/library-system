package com.library.membership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateOrderResponse {
    private String     orderId;           // Gateway order ID or "dev_order_xxx" in dev mode
    private String     membershipId;      // Our internal membership UUID (PENDING state)
    private BigDecimal amount;            // Plan price in INR
    private String     currency;          // "INR"
    private String     gateway;           // "CASHFREE" | "RAZORPAY"
    private String     paymentSessionId;  // Cashfree payment_session_id; null for Razorpay
    private String     razorpayKeyId;     // Razorpay publishable key; null for Cashfree
}