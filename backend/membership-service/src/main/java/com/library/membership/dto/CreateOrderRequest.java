package com.library.membership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {
    @NotBlank
    private String planId;      // UUID of the selected membership plan

    private String seatId;      // UUID of the selected seat (from seat-service)
    private String seatNumber;  // e.g. "A12" — stored denormalised for display
    private String shift;       // MORNING | EVENING | FULL_DAY
    // Required when planType = HALF_DAY
}