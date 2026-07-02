package com.library.membership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "payments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false) private UUID membershipId;
    @Column(name = "user_id",       nullable = false) private UUID userId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(name = "payment_gateway") private String paymentGateway = "RAZORPAY";
    @Column(name = "gateway_order_id")   private String gatewayOrderId;
    @Column(name = "gateway_payment_id") private String gatewayPaymentId;
    @Column(name = "invoice_id") private String invoiceId;

    @Column(nullable = false) @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum Status { PENDING, SUCCESS, FAILED, REFUNDED }
}