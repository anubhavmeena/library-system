package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualStudentImportRequest {
    @NotBlank private String name;
    @NotBlank private String phone;
}
