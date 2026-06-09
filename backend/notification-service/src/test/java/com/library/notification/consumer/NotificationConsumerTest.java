package com.library.notification.consumer;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    NotificationService notificationService;

    @InjectMocks
    NotificationConsumer consumer;

    // ── handleBookingConfirmed ────────────────────────────────────────────────

    @Test
    void handleBookingConfirmed_delegatesToService() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        event.setUserId("user-123");

        consumer.handleBookingConfirmed(event, "booking-confirmed", 0, 100L);

        verify(notificationService).sendBookingConfirmation(event);
    }

    @Test
    void handleBookingConfirmed_serviceThrows_exceptionSwallowed() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        event.setUserId("user-123");
        doThrow(new RuntimeException("send failed")).when(notificationService).sendBookingConfirmation(any());

        // Must NOT propagate — prevents Kafka from re-delivering indefinitely
        assertThatCode(() -> consumer.handleBookingConfirmed(event, "booking-confirmed", 0, 100L))
                .doesNotThrowAnyException();
    }

    @Test
    void handleBookingConfirmed_serviceThrows_serviceCalledOnce() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        doThrow(new RuntimeException("timeout")).when(notificationService).sendBookingConfirmation(any());

        consumer.handleBookingConfirmed(event, "booking-confirmed", 1, 200L);

        verify(notificationService, times(1)).sendBookingConfirmation(event);
    }

    // ── handleUserRegistered ─────────────────────────────────────────────────

    @Test
    void handleUserRegistered_delegatesToService() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        event.setUserId("user-456");
        event.setUserName("Alice");

        consumer.handleUserRegistered(event, "user-registered", 200L);

        verify(notificationService).sendWelcomeNotification(event);
    }

    @Test
    void handleUserRegistered_serviceThrows_exceptionSwallowed() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        doThrow(new RuntimeException("welcome failed")).when(notificationService).sendWelcomeNotification(any());

        assertThatCode(() -> consumer.handleUserRegistered(event, "user-registered", 300L))
                .doesNotThrowAnyException();
    }

    // ── handleRenewalReminder ────────────────────────────────────────────────

    @Test
    void handleRenewalReminder_delegatesToService() {
        RenewalReminderEvent event = new RenewalReminderEvent();
        event.setUserId("user-789");
        event.setDaysRemaining(7);

        consumer.handleRenewalReminder(event, "renewal-reminder", 400L);

        verify(notificationService).sendRenewalReminder(event);
    }

    @Test
    void handleRenewalReminder_serviceThrows_exceptionSwallowed() {
        RenewalReminderEvent event = new RenewalReminderEvent();
        event.setDaysRemaining(3);
        doThrow(new RuntimeException("reminder failed")).when(notificationService).sendRenewalReminder(any());

        assertThatCode(() -> consumer.handleRenewalReminder(event, "renewal-reminder", 500L))
                .doesNotThrowAnyException();
    }

    @Test
    void handleRenewalReminder_doesNotCallBookingConfirmation() {
        RenewalReminderEvent event = new RenewalReminderEvent();

        consumer.handleRenewalReminder(event, "renewal-reminder", 600L);

        verify(notificationService, never()).sendBookingConfirmation(any());
        verify(notificationService, never()).sendWelcomeNotification(any());
    }
}
