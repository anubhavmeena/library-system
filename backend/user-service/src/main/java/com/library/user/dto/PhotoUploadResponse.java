package com.library.user.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PhotoUploadResponse {
    private String photoUrl;
    private String message;
}