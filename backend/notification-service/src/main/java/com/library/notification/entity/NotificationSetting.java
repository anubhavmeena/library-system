package com.library.notification.entity;

import jakarta.persistence.*;
import lombok.*;

// Read-only sibling of admin-service's NotificationSetting entity — same
// convention membership-service uses for its read-only AppSettings mirror.
// admin-service owns writes (via the admin Notification Settings UI); this
// service only ever reads a row here before deciding whether/how to send a
// given notification.
@Entity
@Table(name = "notification_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationSetting {

    @Id
    @Column(name = "notification_key", updatable = false, nullable = false)
    private String notificationKey;

    @Column(name = "send_to_student")
    private boolean sendToStudent;

    @Column(name = "send_to_admin")
    private boolean sendToAdmin;

    @Column(name = "hindi_enabled")
    private boolean hindiEnabled;

    @Column(name = "hindi_text_student", columnDefinition = "TEXT")
    private String hindiTextStudent;

    @Column(name = "hindi_text_admin", columnDefinition = "TEXT")
    private String hindiTextAdmin;
}
