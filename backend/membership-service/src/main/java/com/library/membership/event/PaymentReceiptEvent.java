package com.library.membership.event;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptEvent {
    private String userId;
    private String membershipId;
    private String userName;
    private String userMobile;
    private String userEmail;

    private String invoiceId;
    private String paymentDate;    // yyyy-MM-dd
    private BigDecimal amountPaid;
    private BigDecimal amountPending;
    private String planName;       // nullable
    private String seatNumber;     // nullable
    private String paymentMethod;  // RAZORPAY | CASHFREE | CASH
    private String receiptType;    // NEW_BOOKING | DUES_CLEARED
}
