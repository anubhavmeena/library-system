package com.library.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {
    @NotBlank private String contact;     // mobile number or email
    @NotBlank private String contactType; // MOBILE or EMAIL
}