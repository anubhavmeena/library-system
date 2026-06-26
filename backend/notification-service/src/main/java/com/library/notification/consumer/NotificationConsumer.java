package com.library.notification.consumer;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.dto.SeatAssistanceEvent;
import com.library.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    // ── booking-confirmed → student + admin notification ──────────────────────
    // Published by membership-service after successful Razorpay payment

    @KafkaListener(
            topics = "booking-confirmed",
            groupId = "notification-booking-group",
            containerFactory = "bookingKafkaListenerContainerFactory"
    )
    public void handleBookingConfirmed(
            @Payload BookingConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed [{}] partition={} offset={} userId={}",
                topic, partition, offset, event.getUserId());

        try {
            notificationService.sendBookingConfirmation(event);
        } catch (Exception e) {
            log.error("Failed to process booking-confirmed for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
            // In production: push to a dead-letter topic (booking-confirmed.DLT)
            // or send an alert to the monitoring system
        }
    }

    // ── user-registered → welcome message ─────────────────────────────────────
    // Published by auth-service after new user registration
    // Re-uses BookingConfirmedEvent shape (only userName/userMobile/userEmail used)

    @KafkaListener(
            topics = "user-registered",
            groupId = "notification-booking-group",
            containerFactory = "bookingKafkaListenerContainerFactory"
    )
    public void handleUserRegistered(
            @Payload BookingConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed [{}] offset={} userId={}", topic, offset, event.getUserId());
        try {
            notificationService.sendWelcomeNotification(event);
        } catch (Exception e) {
            log.error("Failed to process user-registered for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    // ── broadcast-notification → admin broadcast to all active members ─────────
    // Published by admin-service POST /api/admin/broadcast

    @KafkaListener(
            topics = "broadcast-notification",
            groupId = "notification-broadcast-group",
            containerFactory = "broadcastKafkaListenerContainerFactory"
    )
    public void handleBroadcast(
            @Payload BroadcastNotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed [{}] offset={} userId={}", topic, offset, event.getUserId());
        try {
            notificationService.sendBroadcast(event);
        } catch (Exception e) {
            log.error("Failed to process broadcast for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    // ── seat-assistance → admin WhatsApp alert ────────────────────────────────
    // Published by membership-service when student taps "Call Admin" on their seat

    @KafkaListener(
            topics = "seat-assistance",
            groupId = "notification-seat-assistance-group",
            containerFactory = "seatAssistanceKafkaListenerContainerFactory"
    )
    public void handleSeatAssistance(
            @Payload SeatAssistanceEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed [{}] offset={} userId={} seat={}",
                topic, offset, event.getUserId(), event.getSeatNumber());
        try {
            notificationService.sendSeatAssistanceAlert(event);
        } catch (Exception e) {
            log.error("Failed to process seat-assistance for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    // ── renewal-reminder → expiry warning ─────────────────────────────────────
    // Published by admin-service scheduler (daily at 9AM) and manually via
    // POST /api/admin/reminders/send

    @KafkaListener(
            topics = "renewal-reminder",
            groupId = "notification-reminder-group",
            containerFactory = "reminderKafkaListenerContainerFactory"
    )
    public void handleRenewalReminder(
            @Payload RenewalReminderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed [{}] offset={} userId={} daysLeft={}",
                topic, offset, event.getUserId(), event.getDaysRemaining());
        try {
            notificationService.sendRenewalReminder(event);
        } catch (Exception e) {
            log.error("Failed to process renewal-reminder for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}