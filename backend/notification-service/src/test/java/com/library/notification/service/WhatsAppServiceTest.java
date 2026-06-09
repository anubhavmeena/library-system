package com.library.notification.service;

import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import com.library.notification.repository.NotificationLogRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import java.util.UUID;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    NotificationLogRepository logRepository;

    @InjectMocks
    WhatsAppService whatsAppService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(whatsAppService, "accountSid", "");
        ReflectionTestUtils.setField(whatsAppService, "authToken", "");
        ReflectionTestUtils.setField(whatsAppService, "whatsappFrom", "whatsapp:+14155238886");
        // twilioEnabled starts false (field default)
    }

    // ── init() ────────────────────────────────────────────────────────────────

    @Test
    void init_blankCredentials_twilioDisabled() {
        whatsAppService.init();

        boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "twilioEnabled");
        assertThat(enabled).isFalse();
    }

    @Test
    void init_withCredentials_initializesTwilioAndEnables() {
        ReflectionTestUtils.setField(whatsAppService, "accountSid", "ACtest123456");
        ReflectionTestUtils.setField(whatsAppService, "authToken", "authtoken123");

        try (MockedStatic<Twilio> mockedTwilio = mockStatic(Twilio.class)) {
            whatsAppService.init();

            mockedTwilio.verify(() -> Twilio.init("ACtest123456", "authtoken123"));
            boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "twilioEnabled");
            assertThat(enabled).isTrue();
        }
    }

    @Test
    void init_onlyAccountSid_twilioDisabled() {
        ReflectionTestUtils.setField(whatsAppService, "accountSid", "ACtest123456");
        ReflectionTestUtils.setField(whatsAppService, "authToken", "");

        whatsAppService.init();

        boolean enabled = (boolean) ReflectionTestUtils.getField(whatsAppService, "twilioEnabled");
        assertThat(enabled).isFalse();
    }

    // ── send() — dev mode (twilioEnabled = false) ─────────────────────────────

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

    // ── send() — Twilio configured ────────────────────────────────────────────

    @Test
    void send_twilioEnabled_success_savedAsSent() {
        ReflectionTestUtils.setField(whatsAppService, "twilioEnabled", true);

        try (MockedStatic<Message> mockedMsg = mockStatic(Message.class)) {
            MessageCreator mockCreator = mock(MessageCreator.class);
            Message mockMessage = mock(Message.class);
            when(mockMessage.getSid()).thenReturn("SM12345");
            when(mockCreator.create()).thenReturn(mockMessage);
            mockedMsg.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                     .thenReturn(mockCreator);

            whatsAppService.send("9876543210", "Hello", UUID.randomUUID().toString(), "BOOKING_CONFIRMED");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(captor.getValue().getErrorMessage()).isNull();
        }
    }

    @Test
    void send_twilioEnabled_exception_savedAsFailed() {
        ReflectionTestUtils.setField(whatsAppService, "twilioEnabled", true);

        try (MockedStatic<Message> mockedMsg = mockStatic(Message.class)) {
            MessageCreator mockCreator = mock(MessageCreator.class);
            when(mockCreator.create()).thenThrow(new RuntimeException("Twilio 429 rate limit"));
            mockedMsg.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                     .thenReturn(mockCreator);

            assertThatCode(() -> whatsAppService.send("9876543210", "Hello", null, "TEST"))
                    .doesNotThrowAnyException();

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(captor.getValue().getErrorMessage()).contains("429");
        }
    }

    @Test
    void send_twilioEnabled_formatsRecipientWithWhatsappPrefix() {
        ReflectionTestUtils.setField(whatsAppService, "twilioEnabled", true);

        try (MockedStatic<Message> mockedMsg = mockStatic(Message.class)) {
            ArgumentCaptor<PhoneNumber> toCaptor = ArgumentCaptor.forClass(PhoneNumber.class);
            MessageCreator mockCreator = mock(MessageCreator.class);
            Message mockMessage = mock(Message.class);
            when(mockMessage.getSid()).thenReturn("SM99");
            when(mockCreator.create()).thenReturn(mockMessage);
            mockedMsg.when(() -> Message.creator(toCaptor.capture(), any(PhoneNumber.class), anyString()))
                     .thenReturn(mockCreator);

            whatsAppService.send("9876543210", "Hi", null, "TEST");

            // Number formatted to whatsapp:+91<number>
            assertThat(toCaptor.getValue().getEndpoint())
                    .isEqualTo("whatsapp:+919876543210");
        }
    }

    // ── formatNumber() — tested via reflection ────────────────────────────────

    @Test
    void formatNumber_tenDigit_prepends91() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", "9876543210");
        assertThat(result).isEqualTo("+919876543210");
    }

    @Test
    void formatNumber_alreadyE164_unchanged() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", "+919876543210");
        assertThat(result).isEqualTo("+919876543210");
    }

    @Test
    void formatNumber_withDashesAndSpaces_stripped() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", "987-654-3210");
        assertThat(result).isEqualTo("+919876543210");
    }

    @Test
    void formatNumber_withParentheses_stripped() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", "(987) 654-3210");
        assertThat(result).isEqualTo("+919876543210");
    }

    @Test
    void formatNumber_null_returnsEmpty() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", (Object) null);
        assertThat(result).isEqualTo("");
    }

    @Test
    void formatNumber_blank_returnsEmpty() {
        String result = ReflectionTestUtils.invokeMethod(whatsAppService, "formatNumber", "   ");
        assertThat(result).isEqualTo("");
    }
}
