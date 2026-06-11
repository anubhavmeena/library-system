package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeSeatRequest {
    @NotBlank private String seatNumber;
}
