package com.library.user.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReceiptUploadResponse {
    private String receiptUrl;
    private String message;
}
