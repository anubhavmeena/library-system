package com.library.admin.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RenewalReminderEvent {
    private String userId;
    private String membershipId;
    private String userName;
    private String userMobile;
    private String userEmail;
    private String seatNumber;
    private String expiryDate;     // yyyy-MM-dd
    private int    daysRemaining;
    private String eventType;      // RENEWAL_REMINDER | PENDING_FEE_REMINDER
    private java.math.BigDecimal pendingAmount; // non-null for PENDING_FEE_REMINDER
}