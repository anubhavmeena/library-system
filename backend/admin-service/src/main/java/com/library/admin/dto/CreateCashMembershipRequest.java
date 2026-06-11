package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCashMembershipRequest {

    @NotBlank
    private String studentId;

    @NotBlank
    private String planId;

    private String shift;        // MORNING | EVENING (required for HALF_DAY plans)

    @NotBlank
    private String seatNumber;   // e.g. "B14"

    private String startDate;    // yyyy-MM-dd; defaults to today if null/blank
}
