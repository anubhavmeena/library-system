package com.library.seat.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatAvailabilityDto {
    private String shift;          // MORNING | EVENING | FULL_DAY
    private String date;           // yyyy-MM-dd
    private int    totalSeats;
    private int    bookedSeats;
    private int    availableSeats;
    private List<SeatDto>              seats;      // flat list of all 110 seats
    private Map<String, List<SeatDto>> seatsByRow; // A -> [...], B -> [...] etc.
}