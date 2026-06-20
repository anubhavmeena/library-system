package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualStudentImportRequest {
    @NotBlank private String name;
    @NotBlank private String phone;
    private String fees;        // optional — matched to closest active plan by price
    private String date;        // yyyy-MM-dd, optional — defaults to today
    @NotBlank private String seatNumber;
}
