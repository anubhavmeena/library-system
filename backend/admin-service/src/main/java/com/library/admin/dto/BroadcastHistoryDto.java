package com.library.admin.dto;

import com.library.admin.entity.BroadcastMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BroadcastHistoryDto {
    private UUID          id;
    private String        message;
    private int           recipientCount;
    private LocalDateTime sentAt;

    public static BroadcastHistoryDto fromEntity(BroadcastMessage b) {
        return BroadcastHistoryDto.builder()
                .id(b.getId())
                .message(b.getMessage())
                .recipientCount(b.getRecipientCount())
                .sentAt(b.getSentAt())
                .build();
    }
}
