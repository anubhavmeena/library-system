package com.library.auth.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OtpVerifyResponse {
    private boolean verified;
    private String sessionToken;
    private boolean isNewUser;
}