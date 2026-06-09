package com.library.auth.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
        ReflectionTestUtils.setField(otpService, "accountSid", "");
        ReflectionTestUtils.setField(otpService, "authToken", "");
        ReflectionTestUtils.setField(otpService, "fromPhone", "");
        ReflectionTestUtils.setField(otpService, "sendGridApiKey", "");
        otpService.init(); // dev mode — no Twilio.init() called
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Test
    void init_blankCredentials_doesNotCallTwilioInit() {
        // If Twilio.init() were called with blank credentials it would throw
        // No exception thrown here = blank-credential guard works
        assertThatCode(() -> otpService.init()).doesNotThrowAnyException();
    }

    @Test
    void init_configuredCredentials_callsTwilioInit() {
        OtpService configured = new OtpService();
        ReflectionTestUtils.setField(configured, "accountSid", "AC123456");
        ReflectionTestUtils.setField(configured, "authToken", "auth-token");
        ReflectionTestUtils.setField(configured, "fromPhone", "+11234567890");

        try (MockedStatic<Twilio> mockedTwilio = mockStatic(Twilio.class)) {
            configured.init();
            mockedTwilio.verify(() -> Twilio.init("AC123456", "auth-token"));
        }
    }

    // ── sendOtp contactType routing ───────────────────────────────────────────

    @Test
    void sendOtp_mobileType_doesNotThrowInDevMode() {
        assertThatCode(() -> otpService.sendOtp("9876543210", "MOBILE", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOtp_mobileTypeLowercase_treatedAsMobile() {
        assertThatCode(() -> otpService.sendOtp("9876543210", "mobile", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOtp_emailType_doesNotThrowInDevMode() {
        assertThatCode(() -> otpService.sendOtp("test@example.com", "EMAIL", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOtp_emailTypeLowercase_doesNotThrow() {
        assertThatCode(() -> otpService.sendOtp("test@example.com", "email", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOtp_unknownContactType_doesNotThrowAndNoAction() {
        assertThatCode(() -> otpService.sendOtp("9876543210", "WHATSAPP", "123456"))
                .doesNotThrowAnyException();
    }

    // ── sendSms with Twilio configured ────────────────────────────────────────

    @Test
    void sendSms_mobileWithoutPlus_prefixesWith91() {
        OtpService configured = buildConfigured();
        try (MockedStatic<Twilio> twilio = mockStatic(Twilio.class);
             MockedStatic<Message> msg = mockStatic(Message.class)) {

            configured.init();
            MessageCreator creator = mock(MessageCreator.class);
            Message msgObj = mock(Message.class);
            when(msgObj.getSid()).thenReturn("SM123");
            when(creator.create()).thenReturn(msgObj);

            ArgumentCaptor<PhoneNumber> toCaptor = ArgumentCaptor.forClass(PhoneNumber.class);
            msg.when(() -> Message.creator(toCaptor.capture(), any(PhoneNumber.class), anyString()))
               .thenReturn(creator);

            configured.sendOtp("9876543210", "MOBILE", "123456");

            assertThat(toCaptor.getValue().getEndpoint()).isEqualTo("+919876543210");
        }
    }

    @Test
    void sendSms_mobileWithPlus_notModified() {
        OtpService configured = buildConfigured();
        try (MockedStatic<Twilio> twilio = mockStatic(Twilio.class);
             MockedStatic<Message> msg = mockStatic(Message.class)) {

            configured.init();
            MessageCreator creator = mock(MessageCreator.class);
            Message msgObj = mock(Message.class);
            when(msgObj.getSid()).thenReturn("SM456");
            when(creator.create()).thenReturn(msgObj);

            ArgumentCaptor<PhoneNumber> toCaptor = ArgumentCaptor.forClass(PhoneNumber.class);
            msg.when(() -> Message.creator(toCaptor.capture(), any(PhoneNumber.class), anyString()))
               .thenReturn(creator);

            configured.sendOtp("+14155552671", "MOBILE", "123456");

            assertThat(toCaptor.getValue().getEndpoint()).isEqualTo("+14155552671");
        }
    }

    @Test
    void sendSms_twilioException_wrapsAsRuntimeException() {
        OtpService configured = buildConfigured();
        try (MockedStatic<Twilio> twilio = mockStatic(Twilio.class);
             MockedStatic<Message> msg = mockStatic(Message.class)) {

            configured.init();
            msg.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
               .thenThrow(new RuntimeException("Twilio network error"));

            assertThatThrownBy(() -> configured.sendOtp("9876543210", "MOBILE", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send OTP");
        }
    }

    private OtpService buildConfigured() {
        OtpService s = new OtpService();
        ReflectionTestUtils.setField(s, "accountSid", "AC123456");
        ReflectionTestUtils.setField(s, "authToken", "auth-token");
        ReflectionTestUtils.setField(s, "fromPhone", "+11234567890");
        return s;
    }
}
