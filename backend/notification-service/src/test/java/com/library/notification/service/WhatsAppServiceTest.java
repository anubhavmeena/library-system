package com.library.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import com.library.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock NotificationLogRepository logRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks WhatsAppService whatsAppService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(whatsAppService, "metaToken", "");
        ReflectionTestUtils.setField(whatsAppService, "metaPhoneNumberId", "");
        ReflectionTestUtils.setField(whatsAppService, "metaApiVersion", "v21.0");
        ReflectionTestUtils.setField(whatsAppService, "metaTemplateName", "library_notification");
        ReflectionTestUtils.setField(whatsAppService, "receiptTemplateName", "payment_receipt");
        ReflectionTestUtils.setField(whatsAppService, "metaLanguage", "en_US");
        // metaEnabled starts false (field default)
    }

    // ── init() ────────────────────────────────────────────────────────────────

    @Test
    void init_blankCredentials_metaDisabled() {
        whatsAppService.init();

        boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "metaEnabled");
        assertThat(enabled).isFalse();
    }

    @Test
    void init_withCredentials_metaEnabled() {
        ReflectionTestUtils.setField(whatsAppService, "metaToken", "EAAtest123");
        ReflectionTestUtils.setField(whatsAppService, "metaPhoneNumberId", "123456789");

        whatsAppService.init();

        boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "metaEnabled");
        assertThat(enabled).isTrue();
    }

    @Test
    void init_onlyToken_metaDisabled() {
        ReflectionTestUtils.setField(whatsAppService, "metaToken", "EAAtest123");
        ReflectionTestUtils.setField(whatsAppService, "metaPhoneNumberId", "");

        whatsAppService.init();

        boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "metaEnabled");
        assertThat(enabled).isFalse();
    }

    // ── send() — dev mode (metaEnabled = false) ───────────────────────────────

    @Test
    void send_devMode_savesLogWithSentStatus() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        String userId = UUID.randomUUID().toString();

        whatsAppService.send("9876543210", "Hello", userId, "BOOKING_CONFIRMED");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(captor.getValue().getChannel()).isEqualTo(Channel.WHATSAPP);
        assertThat(captor.getValue().getRecipient()).isEqualTo("9876543210");
        assertThat(captor.getValue().getEvent()).isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    void send_devMode_nullUserId_savedWithNullUserId() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        whatsAppService.send("+911234567890", "Admin alert", null, "ADMIN_BOOKING_ALERT");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void send_devMode_messageBodySaved() {
        String message = "Booking confirmed for seat A1";
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        whatsAppService.send("9876543210", message, null, "TEST");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo(message);
    }

    @Test
    void send_dbFailure_swallowed() {
        doThrow(new RuntimeException("DB down")).when(logRepository).save(any());

        assertThatCode(() -> whatsAppService.send("9876543210", "Hello", null, "TEST"))
                .doesNotThrowAnyException();
    }

    // ── send() — metaEnabled = false, coverage of log fields ─────────────────

    @Test
    void send_devMode_statusIsSent_noErrorMessage() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        whatsAppService.send("9876543210", "Hello", UUID.randomUUID().toString(), "RENEWAL_REMINDER");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(captor.getValue().getErrorMessage()).isNull();
    }

    // ── sendDocumentTemplate() — dev mode (metaEnabled = false) ──────────────

    @Test
    void sendDocumentTemplate_devMode_savesLogWithSentStatus() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        String userId = UUID.randomUUID().toString();

        whatsAppService.sendDocumentTemplate(
                "9876543210", "https://targetzone.co.in/uploads/receipts/INV-1.pdf", "INV-1.pdf",
                List.of("Arjun", "300", "INV-1", "20/03/2026", "0"),
                userId, "PAYMENT_RECEIPT");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(captor.getValue().getChannel()).isEqualTo(Channel.WHATSAPP);
        assertThat(captor.getValue().getRecipient()).isEqualTo("9876543210");
        assertThat(captor.getValue().getEvent()).isEqualTo("PAYMENT_RECEIPT");
        assertThat(captor.getValue().getUserId()).isEqualTo(UUID.fromString(userId));
    }

    @Test
    void sendDocumentTemplate_devMode_nullUserId_savedWithNullUserId() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        whatsAppService.sendDocumentTemplate(
                "9876543210", "https://targetzone.co.in/uploads/receipts/INV-1.pdf", "INV-1.pdf",
                List.of("Arjun", "300", "INV-1", "20/03/2026", "0"),
                null, "PAYMENT_RECEIPT_ADMIN");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void sendDocumentTemplate_dbFailure_swallowed() {
        doThrow(new RuntimeException("DB down")).when(logRepository).save(any());

        assertThatCode(() -> whatsAppService.sendDocumentTemplate(
                "9876543210", "https://targetzone.co.in/uploads/receipts/INV-1.pdf", "INV-1.pdf",
                List.of("Arjun", "300", "INV-1", "20/03/2026", "0"),
                null, "PAYMENT_RECEIPT"))
                .doesNotThrowAnyException();
    }
}
