package com.library.user.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IdCardUploadResponse {
    private String idCardUrl;
    private String message;
}
