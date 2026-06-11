package com.library.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastRequest {
    @NotBlank
    @Size(min = 5, max = 1000)
    private String message;
}
