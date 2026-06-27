package com.library.membership.event;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatAssistanceEvent {
    private String userId;
    private String userName;
    private String seatNumber;
    private String eventType;
    private String adminMobile;
}
