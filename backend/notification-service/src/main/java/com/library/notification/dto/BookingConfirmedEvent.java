package com.library.notification.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BookingConfirmedEvent {
    private String userId, membershipId;
    private String userName, userMobile, userEmail;
    private String planName, planType;
    private String seatNumber, shift;
    private String startDate, endDate;
    private BigDecimal amountPaid;
    private String wifiName, wifiPassword;
    private String eventType;
}