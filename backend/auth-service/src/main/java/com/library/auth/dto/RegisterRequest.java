package com.library.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank private String name;
    private String mobile;
    private String email;
    @NotBlank private String sessionToken; // from OTP verification step
    private String dateOfBirth;
    private String gender;
    private String address;
}