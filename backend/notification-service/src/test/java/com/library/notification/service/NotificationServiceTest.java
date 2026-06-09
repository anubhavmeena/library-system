package com.library.notification.service;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.RenewalReminderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    WhatsAppService whatsAppService;

    @Mock
    EmailService emailService;

    @InjectMocks
    NotificationService notificationService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(notificationService, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(notificationService, "adminWhatsapp", "");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingConfirmedEvent bookingEvent(String mobile, String email) {
        BookingConfirmedEvent e = new BookingConfirmedEvent();
        e.setUserId("user-123");
        e.setUserName("Alice");
        e.setUserMobile(mobile);
        e.setUserEmail(email);
        e.setPlanName("Monthly Plan");
        e.setSeatNumber("A1");
        e.setShift("MORNING");
        e.setStartDate("2025-01-01");
        e.setEndDate("2025-01-31");
        e.setAmountPaid(new BigDecimal("600"));
        return e;
    }

    private RenewalReminderEvent reminderEvent(int days) {
        RenewalReminderEvent e = new RenewalReminderEvent();
        e.setUserId("user-r1");
        e.setUserName("Charlie");
        e.setUserMobile("9123456789");
        e.setUserEmail("charlie@test.com");
        e.setSeatNumber("C5");
        e.setExpiryDate("2025-02-01");
        e.setDaysRemaining(days);
        return e;
    }

    // ── sendBookingConfirmation ───────────────────────────────────────────────

    @Test
    void sendBookingConfirmation_mobileAndEmail_sendsBoth() {
        notificationService.sendBookingConfirmation(bookingEvent("9876543210", "alice@test.com"));

        verify(whatsAppService).send(eq("9876543210"), anyString(), eq("user-123"), eq("BOOKING_CONFIRMED"));
        verify(emailService).sendText(eq("alice@test.com"), anyString(), anyString(), eq("user-123"), eq("BOOKING_CONFIRMED"));
    }

    @Test
    void sendBookingConfirmation_adminEmailAlwaysSent() {
        notificationService.sendBookingConfirmation(bookingEvent(null, null));

        verify(emailService).sendText(eq("admin@test.com"), anyString(), anyString(), isNull(), eq("ADMIN_BOOKING_ALERT"));
    }

    @Test
    void sendBookingConfirmation_adminWhatsappNotConfigured_noAdminWhatsApp() {
        notificationService.sendBookingConfirmation(bookingEvent("9876543210", null));

        // Only 1 WhatsApp (to student), not to blank admin
        verify(whatsAppService, times(1)).send(anyString(), anyString(), any(), anyString());
    }

    @Test
    void sendBookingConfirmation_adminWhatsappConfigured_sendsToAdmin() {
        ReflectionTestUtils.setField(notificationService, "adminWhatsapp", "+911234567890");
        notificationService.sendBookingConfirmation(bookingEvent("9876543210", null));

        verify(whatsAppService, times(2)).send(anyString(), anyString(), any(), anyString());
        verify(whatsAppService).send(eq("+911234567890"), anyString(), isNull(), eq("ADMIN_BOOKING_ALERT"));
    }

    @Test
    void sendBookingConfirmation_nullMobile_noStudentWhatsApp() {
        notificationService.sendBookingConfirmation(bookingEvent(null, "alice@test.com"));

        verify(whatsAppService, never()).send(anyString(), anyString(), eq("user-123"), eq("BOOKING_CONFIRMED"));
    }

    @Test
    void sendBookingConfirmation_blankMobile_noStudentWhatsApp() {
        notificationService.sendBookingConfirmation(bookingEvent("", "alice@test.com"));

        verify(whatsAppService, never()).send(anyString(), anyString(), eq("user-123"), eq("BOOKING_CONFIRMED"));
    }

    @Test
    void sendBookingConfirmation_nullEmail_noStudentEmail() {
        notificationService.sendBookingConfirmation(bookingEvent("9876543210", null));

        verify(emailService, never()).sendText(anyString(), anyString(), anyString(), eq("user-123"), anyString());
    }

    @Test
    void sendBookingConfirmation_blankEmail_noStudentEmail() {
        notificationService.sendBookingConfirmation(bookingEvent("9876543210", ""));

        verify(emailService, never()).sendText(anyString(), anyString(), anyString(), eq("user-123"), anyString());
    }

    @Test
    void sendBookingConfirmation_shiftMorning_messageContainsMorning() {
        BookingConfirmedEvent event = bookingEvent("9876543210", null);
        event.setShift("MORNING");
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendBookingConfirmation(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("Morning");
    }

    @Test
    void sendBookingConfirmation_shiftEvening_messageContainsEvening() {
        BookingConfirmedEvent event = bookingEvent("9876543210", null);
        event.setShift("EVENING");
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendBookingConfirmation(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("Evening");
    }

    @Test
    void sendBookingConfirmation_shiftNull_fullDayInMessage() {
        BookingConfirmedEvent event = bookingEvent("9876543210", null);
        event.setShift(null);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendBookingConfirmation(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("Full Day");
    }

    @Test
    void sendBookingConfirmation_unknownShift_fullDayInMessage() {
        BookingConfirmedEvent event = bookingEvent("9876543210", null);
        event.setShift("UNKNOWN_SHIFT");
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendBookingConfirmation(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("Full Day");
    }

    // ── sendWelcomeNotification ───────────────────────────────────────────────

    @Test
    void sendWelcomeNotification_mobileAndEmail_sendsBoth() {
        notificationService.sendWelcomeNotification(bookingEvent("9876543210", "alice@test.com"));

        verify(whatsAppService).send(eq("9876543210"), anyString(), eq("user-123"), eq("USER_REGISTERED"));
        verify(emailService).sendText(eq("alice@test.com"), eq("Welcome to Target Zone Library! 📚"),
                anyString(), eq("user-123"), eq("USER_REGISTERED"));
    }

    @Test
    void sendWelcomeNotification_blankMobile_onlyEmail() {
        notificationService.sendWelcomeNotification(bookingEvent("", "alice@test.com"));

        verify(whatsAppService, never()).send(anyString(), anyString(), anyString(), eq("USER_REGISTERED"));
        verify(emailService).sendText(anyString(), anyString(), anyString(), anyString(), eq("USER_REGISTERED"));
    }

    @Test
    void sendWelcomeNotification_blankEmail_onlyWhatsApp() {
        notificationService.sendWelcomeNotification(bookingEvent("9876543210", ""));

        verify(whatsAppService).send(anyString(), anyString(), anyString(), eq("USER_REGISTERED"));
        verify(emailService, never()).sendText(anyString(), anyString(), anyString(), anyString(), eq("USER_REGISTERED"));
    }

    @Test
    void sendWelcomeNotification_messageContainsUserName() {
        BookingConfirmedEvent event = bookingEvent("9876543210", null);
        event.setUserName("Bob");
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendWelcomeNotification(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("Bob");
    }

    @Test
    void sendWelcomeNotification_noMobileNoEmail_noCalls() {
        notificationService.sendWelcomeNotification(bookingEvent(null, null));

        verifyNoInteractions(whatsAppService);
        verifyNoInteractions(emailService);
    }

    // ── sendRenewalReminder ───────────────────────────────────────────────────

    @Test
    void sendRenewalReminder_daysRemaining3_urgentLabel() {
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(reminderEvent(3));

        verify(emailService).sendText(anyString(), subjectCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(subjectCaptor.getValue()).contains("URGENT");
    }

    @Test
    void sendRenewalReminder_daysRemaining1_urgentAndSingularDay() {
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(reminderEvent(1));

        verify(emailService).sendText(anyString(), subjectCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(subjectCaptor.getValue()).contains("URGENT");
        // "1 day" not "1 days"
        assertThat(subjectCaptor.getValue()).endsWith("1 day");
    }

    @Test
    void sendRenewalReminder_daysRemaining7_reminderLabel() {
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(reminderEvent(7));

        verify(emailService).sendText(anyString(), subjectCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(subjectCaptor.getValue()).contains("Reminder");
        assertThat(subjectCaptor.getValue()).contains("7 days");
    }

    @Test
    void sendRenewalReminder_daysRemaining4_reminderNotUrgent() {
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(reminderEvent(4));

        verify(emailService).sendText(anyString(), subjectCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(subjectCaptor.getValue()).doesNotContain("URGENT");
        assertThat(subjectCaptor.getValue()).contains("Reminder");
    }

    @Test
    void sendRenewalReminder_nullSeatNumber_showsNA() {
        RenewalReminderEvent event = reminderEvent(7);
        event.setSeatNumber(null);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("N/A");
    }

    @Test
    void sendRenewalReminder_seatNumberPresent_shownInMessage() {
        RenewalReminderEvent event = reminderEvent(7);
        event.setSeatNumber("B12");
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);

        notificationService.sendRenewalReminder(event);

        verify(whatsAppService).send(anyString(), msgCaptor.capture(), anyString(), anyString());
        assertThat(msgCaptor.getValue()).contains("B12");
    }

    @Test
    void sendRenewalReminder_nullMobile_noWhatsApp() {
        RenewalReminderEvent event = reminderEvent(7);
        event.setUserMobile(null);

        notificationService.sendRenewalReminder(event);

        verify(whatsAppService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendRenewalReminder_nullEmail_noEmail() {
        RenewalReminderEvent event = reminderEvent(7);
        event.setUserEmail(null);

        notificationService.sendRenewalReminder(event);

        verify(emailService, never()).sendText(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendRenewalReminder_correctEventLabel() {
        notificationService.sendRenewalReminder(reminderEvent(7));

        verify(whatsAppService).send(anyString(), anyString(), eq("user-r1"), eq("RENEWAL_REMINDER"));
        verify(emailService).sendText(anyString(), anyString(), anyString(), eq("user-r1"), eq("RENEWAL_REMINDER"));
    }
}
