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
}
