package com.library.membership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateOrderResponse {
    private String     orderId;        // Razorpay order ID (e.g. "order_xxxxx")
    // or "dev_order_xxx" in dev mode
    private String     membershipId;   // Our internal membership UUID (PENDING state)
    private BigDecimal amount;         // Plan price in INR (e.g. 400.00 or 600.00)
    private String     currency;       // "INR"
    private String     razorpayKeyId;  // Razorpay publishable key — sent to frontend
    // so it can open the Razorpay checkout modal
}