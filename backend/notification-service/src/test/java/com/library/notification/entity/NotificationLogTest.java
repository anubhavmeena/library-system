package com.library.notification.entity;

import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLogTest {

    @Test
    void prePersist_setsCreatedAtWhenNull() throws Exception {
        NotificationLog log = NotificationLog.builder()
                .event("TEST")
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .build();
        assertThat(log.getCreatedAt()).isNull();

        invokePersist(log);

        assertThat(log.getCreatedAt()).isNotNull().isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void prePersist_doesNotOverrideExistingCreatedAt() throws Exception {
        LocalDateTime existing = LocalDateTime.of(2025, 1, 1, 10, 0);
        NotificationLog log = NotificationLog.builder()
                .event("TEST")
                .createdAt(existing)
                .build();

        invokePersist(log);

        assertThat(log.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void builder_allFieldsSet() {
        UUID userId = UUID.randomUUID();
        NotificationLog log = NotificationLog.builder()
                .userId(userId)
                .recipient("user@test.com")
                .message("Hello world")
                .event("BOOKING_CONFIRMED")
                .channel(Channel.WHATSAPP)
                .status(DeliveryStatus.SENT)
                .errorMessage(null)
                .build();

        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getRecipient()).isEqualTo("user@test.com");
        assertThat(log.getMessage()).isEqualTo("Hello world");
        assertThat(log.getEvent()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(log.getChannel()).isEqualTo(Channel.WHATSAPP);
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void builder_withNullUserId_adminAlerts() {
        NotificationLog log = NotificationLog.builder()
                .userId(null)
                .event("ADMIN_BOOKING_ALERT")
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .build();

        assertThat(log.getUserId()).isNull();
    }

    @Test
    void noArgsConstructor_allFieldsNull() {
        NotificationLog log = new NotificationLog();

        assertThat(log.getStatus()).isNull();
        assertThat(log.getChannel()).isNull();
        assertThat(log.getCreatedAt()).isNull();
        assertThat(log.getUserId()).isNull();
    }

    @Test
    void builder_failedStatus_withErrorMessage() {
        NotificationLog log = NotificationLog.builder()
                .event("BOOKING_CONFIRMED")
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.FAILED)
                .errorMessage("SendGrid HTTP 400: Bad Request")
                .build();

        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("400");
    }

    private void invokePersist(NotificationLog log) throws Exception {
        Method method = NotificationLog.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);
        method.invoke(log);
    }
}
