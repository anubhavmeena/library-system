package com.library.notification.dto;

import lombok.Data;

@Data
public class RenewalReminderEvent {
    private String userId, membershipId;
    private String userName, userMobile, userEmail;
    private String seatNumber;
    private String expiryDate;
    private int    daysRemaining;
    private String eventType;
}