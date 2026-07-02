package com.library.notification.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentReceiptEvent {
    private String userId, membershipId;
    private String userName, userMobile, userEmail;

    private String invoiceId;
    private String paymentDate;
    private BigDecimal amountPaid;
    private BigDecimal amountPending;
    private String planName;
    private String seatNumber;
    private String paymentMethod;
    private String receiptType;
}
