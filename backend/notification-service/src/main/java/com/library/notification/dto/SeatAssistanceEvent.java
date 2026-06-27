package com.library.notification.dto;

import lombok.Data;

@Data
public class SeatAssistanceEvent {
    private String userId;
    private String userName;
    private String seatNumber;
    private String eventType;
    private String adminMobile;
}
