package com.library.seat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExtendBookingRequest {
    @NotBlank private String newEndDate; // yyyy-MM-dd
}
