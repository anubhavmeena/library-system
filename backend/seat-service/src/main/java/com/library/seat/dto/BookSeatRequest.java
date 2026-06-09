package com.library.seat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookSeatRequest {
    @NotBlank private String seatNumber;   // e.g. "B14"
    @NotBlank private String membershipId; // UUID of the active membership
    @NotBlank private String shift;        // MORNING | EVENING | FULL_DAY
    @NotBlank private String startDate;    // yyyy-MM-dd
    @NotBlank private String endDate;      // yyyy-MM-dd
}