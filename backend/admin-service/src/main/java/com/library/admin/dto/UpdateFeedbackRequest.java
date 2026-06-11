package com.library.admin.dto;

import lombok.Data;

@Data
public class UpdateFeedbackRequest {
    private String status;
    private String adminNotes;
}
