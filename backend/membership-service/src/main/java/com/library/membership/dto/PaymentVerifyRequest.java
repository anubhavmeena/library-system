package com.library.membership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentVerifyRequest {
    @NotBlank
    private String gatewayOrderId;    // razorpay_order_id   — from Razorpay callback

    @NotBlank
    private String gatewayPaymentId;  // razorpay_payment_id — from Razorpay callback

    private String signature;         // razorpay_signature  — HMAC-SHA256 of
    // (orderId + "|" + paymentId) signed with key secret

    private String membershipId;      // Our internal membership UUID
    // Used in dev mode to locate the record
    // without a real gateway order ID
}