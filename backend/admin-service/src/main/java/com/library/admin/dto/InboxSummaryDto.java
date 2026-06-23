package com.library.admin.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InboxSummaryDto {
    private int     messageNumber;
    private String  from;
    private String  subject;
    private String  date;
    private boolean isRead;
}
