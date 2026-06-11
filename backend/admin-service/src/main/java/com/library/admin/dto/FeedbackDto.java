package com.library.admin.dto;

import com.library.admin.entity.Feedback;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeedbackDto {
    private String id;
    private String userId;
    private String studentName;
    private String studentMobile;
    private String type;
    private String subject;
    private String description;
    private String status;
    private String adminNotes;
    private String createdAt;
    private String updatedAt;

    public static FeedbackDto fromEntity(Feedback f) {
        return FeedbackDto.builder()
                .id(f.getId().toString())
                .userId(f.getUserId().toString())
                .studentName(f.getUser() != null ? f.getUser().getName() : "Unknown")
                .studentMobile(f.getUser() != null ? f.getUser().getMobile() : null)
                .type(f.getType().name())
                .subject(f.getSubject())
                .description(f.getDescription())
                .status(f.getStatus().name())
                .adminNotes(f.getAdminNotes())
                .createdAt(f.getCreatedAt() != null ? f.getCreatedAt().toString() : null)
                .updatedAt(f.getUpdatedAt() != null ? f.getUpdatedAt().toString() : null)
                .build();
    }
}
