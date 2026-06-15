package com.library.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String sessionToken; // student login — exchanged from verify-otp session
    private String contact;      // admin login
    private String otp;          // admin login
}