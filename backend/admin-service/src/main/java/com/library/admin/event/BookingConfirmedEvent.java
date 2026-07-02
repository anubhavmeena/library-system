package com.library.admin.event;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingConfirmedEvent {
    private String userId;
    private String membershipId;
    private String userName;
    private String userMobile;
    private String userEmail;
    private String planName;
    private String planType;
    private String seatNumber;
    private String shift;
    private String startDate;
    private String endDate;
    private BigDecimal amountPaid;
    private String wifiName;
    private String wifiPassword;
}
