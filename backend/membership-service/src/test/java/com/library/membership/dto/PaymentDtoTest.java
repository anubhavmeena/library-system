package com.library.membership.dto;

import com.library.membership.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PaymentDtoTest {

    private Payment buildPayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .membershipId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(600))
                .gatewayOrderId("order_abc123")
                .gatewayPaymentId("pay_xyz789")
                .status(Payment.Status.SUCCESS)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        Payment p = buildPayment();
        PaymentDto dto = PaymentDto.fromEntity(p);

        assertThat(dto.getId()).isEqualTo(p.getId().toString());
        assertThat(dto.getMembershipId()).isEqualTo(p.getMembershipId().toString());
        assertThat(dto.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(dto.getGatewayOrderId()).isEqualTo("order_abc123");
        assertThat(dto.getGatewayPaymentId()).isEqualTo("pay_xyz789");
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void fromEntity_pendingPayment_gatewayPaymentIdNull() {
        Payment p = buildPayment();
        p.setGatewayPaymentId(null);
        p.setStatus(Payment.Status.PENDING);

        PaymentDto dto = PaymentDto.fromEntity(p);

        assertThat(dto.getGatewayPaymentId()).isNull();
        assertThat(dto.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void fromEntity_nullCreatedAt_createdAtNullInDto() {
        Payment p = buildPayment();
        p.setCreatedAt(null);

        assertThat(PaymentDto.fromEntity(p).getCreatedAt()).isNull();
    }

    @Test
    void fromEntity_failedStatus_mappedCorrectly() {
        Payment p = buildPayment();
        p.setStatus(Payment.Status.FAILED);

        assertThat(PaymentDto.fromEntity(p).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void fromEntity_refundedStatus_mappedCorrectly() {
        Payment p = buildPayment();
        p.setStatus(Payment.Status.REFUNDED);

        assertThat(PaymentDto.fromEntity(p).getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void fromEntity_paymentGatewayFieldPreserved() {
        Payment p = buildPayment();
        // paymentGateway defaults to "RAZORPAY" via field initializer (but Builder ignores it)
        // Test that fromEntity maps whatever is stored
        assertThat(PaymentDto.fromEntity(p).getPaymentGateway()).isNull();
    }
}
