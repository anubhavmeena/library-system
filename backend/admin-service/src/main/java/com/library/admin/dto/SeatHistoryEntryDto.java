package com.library.admin.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatHistoryEntryDto {
    private String membershipId;
    private String studentName;
    private String studentMobile;
    private String shift;
    private String startDate;   // yyyy-MM-dd
    private String endDate;     // yyyy-MM-dd
    private String status;      // ACTIVE | GRACE | EXPIRED | QUEUED

    // Only populated by the per-student history endpoint (getStudentSeatHistory) —
    // null on the per-seat endpoint (getSeatHistory), since the seat is already
    // known from the request path there.
    private String seatNumber;
    private String planName;
}
