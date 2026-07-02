package com.library.admin.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatMapDto {
    private String shift;
    private String date;
    private int    totalSeats;
    private int    occupiedSeats;
    private int    availableSeats;

    // A -> [SeatInfoDto x28], B -> [x28], C -> [x28], D -> [x26]
    private Map<String, List<SeatInfoDto>> seatsByRow;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SeatInfoDto {
        private String  seatNumber;
        private Boolean isOccupied;
        private String  studentName;    // null when seat is available
        private String  studentMobile;  // null when seat is available
        private String  studentGender;  // null when seat is available
        private String  shift;          // the shift the seat is booked for
        private String  membershipEnd;  // yyyy-MM-dd
        private String  membershipId;      // null when seat is available
        private String  membershipStatus;  // ACTIVE | GRACE, null when seat is available
        private Integer daysOverdue;       // today - membershipEnd; positive = overdue (GRACE), only set for GRACE seats
    }
}