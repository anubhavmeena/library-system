package com.library.membership.dto;

import com.library.membership.entity.Payment;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentDto {
    private String     id;
    private String     membershipId;
    private BigDecimal amount;
    private String     paymentGateway;    // RAZORPAY
    private String     gatewayOrderId;    // rzp_order_xxx
    private String     gatewayPaymentId;  // rzp_pay_xxx  (null until verified)
    private String     status;            // PENDING | SUCCESS | FAILED | REFUNDED
    private String     createdAt;

    public static PaymentDto fromEntity(Payment p) {
        return PaymentDto.builder()
                .id(p.getId().toString())
                .membershipId(p.getMembershipId().toString())
                .amount(p.getAmount())
                .paymentGateway(p.getPaymentGateway())
                .gatewayOrderId(p.getGatewayOrderId())
                .gatewayPaymentId(p.getGatewayPaymentId())
                .status(p.getStatus().name())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
                .build();
    }
}