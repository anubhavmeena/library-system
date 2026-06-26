package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMembershipPlanRequest {
    @NotBlank
    private String planId;
}
