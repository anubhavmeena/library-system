package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// One row per notification type (see NotificationSettingsService.NOTIFICATION_DEFAULTS
// for the fixed key catalog) — not a singleton like AppSettings. admin-service owns
// writes; notification-service has a read-only mirror of this same table (same
// convention as membership-service's read-only AppSettings mirror) and checks it
// before every WhatsApp/email send.
@Entity
@Table(name = "notification_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationSetting {

    @Id
    @Column(name = "notification_key", updatable = false, nullable = false)
    private String notificationKey;

    @Column(name = "send_to_student", nullable = false)
    private boolean sendToStudent;

    @Column(name = "send_to_admin", nullable = false)
    private boolean sendToAdmin;

    @Column(name = "hindi_enabled", nullable = false)
    private boolean hindiEnabled;

    @Column(name = "hindi_text_student", columnDefinition = "TEXT")
    private String hindiTextStudent;

    @Column(name = "hindi_text_admin", columnDefinition = "TEXT")
    private String hindiTextAdmin;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
