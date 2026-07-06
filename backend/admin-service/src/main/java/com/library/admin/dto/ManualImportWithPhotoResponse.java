package com.library.admin.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ManualImportWithPhotoResponse {
    private String message;
    private String photoUrl;   // null when no photo was submitted
}
