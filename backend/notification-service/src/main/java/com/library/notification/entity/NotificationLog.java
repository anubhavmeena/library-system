package com.library.notification.entity;

import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID   userId;
    private String recipient;
    private String message;
    private String event;

    @Enumerated(EnumType.STRING)
    Channel channel;

    @Enumerated(EnumType.STRING)
    DeliveryStatus status;

    private String        errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (sentAt   == null) sentAt    = LocalDateTime.now();
    }
}