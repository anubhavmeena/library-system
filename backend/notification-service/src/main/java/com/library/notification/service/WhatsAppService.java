package com.library.notification.service;

import com.library.notification.entity.NotificationLog;
import com.library.notification.repository.NotificationLogRepository;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final NotificationLogRepository logRepository;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.whatsapp-from:whatsapp:+14155238886}")
    private String whatsappFrom;

    private boolean twilioEnabled = false;

    /**
     * Initialises the Twilio SDK on startup.
     * If credentials are not configured (dev mode), messages are only logged
     * to the console — no real WhatsApp messages are sent.
     */
    @PostConstruct
    public void init() {
        if (!accountSid.isBlank() && !authToken.isBlank()) {
            com.twilio.Twilio.init(accountSid, authToken);
            twilioEnabled = true;
            log.info("Twilio WhatsApp initialized successfully");
        } else {
            log.warn("Twilio not configured — WhatsApp messages will be logged only (dev mode)");
        }
    }

    /**
     * Send a WhatsApp message via Twilio and persist a delivery log entry.
     *
     * @param mobile   Recipient mobile number — 10-digit Indian (auto-prefixed +91)
     *                 or full E.164 format (e.g. +919876543210)
     * @param message  WhatsApp message body (plain text, supports *bold* formatting)
     * @param userId   Student UUID for audit log — nullable (e.g. admin alerts)
     * @param event    Event type label stored in notification_logs
     *                 e.g. BOOKING_CONFIRMED, RENEWAL_REMINDER, ADMIN_BOOKING_ALERT
     */
    public void send(String mobile, String message, String userId, String event) {
        String formatted                        = formatNumber(mobile);
        DeliveryStatus status   = DeliveryStatus.SENT;
        String errorMessage                     = null;

        if (!twilioEnabled) {
            // Dev mode — log to console, no actual send
            log.info("[DEV] WhatsApp → {} | Event: {} | Message:\n{}", mobile, event, message);
        } else {
            try {
                Message msg = Message.creator(
                        new PhoneNumber("whatsapp:" + formatted),  // To
                        new PhoneNumber(whatsappFrom),              // From (Twilio sandbox or approved number)
                        message
                ).create();

                log.info("WhatsApp sent to {} | SID: {}", mobile, msg.getSid());

            } catch (Exception e) {
                status      = DeliveryStatus.FAILED;
                errorMessage = e.getMessage();
                log.error("WhatsApp send failed to {}: {}", mobile, e.getMessage());
                // Do NOT rethrow — notification failure must not crash the Kafka consumer
            }
        }

        saveLog(userId, mobile, message, event, status, errorMessage);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Normalises a mobile number to E.164 format with Indian country code.
     * Strips all non-numeric characters (spaces, dashes, parentheses).
     * If the number already starts with + it is used as-is.
     */
    private String formatNumber(String mobile) {
        if (mobile == null || mobile.isBlank()) return "";
        mobile = mobile.replaceAll("[^0-9+]", "");
        return mobile.startsWith("+") ? mobile : "+91" + mobile;
    }

    /**
     * Persists every send attempt to notification_logs — both successes and failures.
     * Wrapped in try-catch so a DB write failure never prevents the message from
     * being delivered (or at least attempted).
     */
    private void saveLog(String userId, String recipient, String message,
                         String event, DeliveryStatus status,
                         String errorMessage) {
        try {
            NotificationLog entry = NotificationLog.builder()
                    .userId(userId != null ? UUID.fromString(userId) : null)
                    .channel(Channel.WHATSAPP)
                    .event(event)
                    .recipient(recipient)
                    .message(message)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to save WhatsApp notification log: {}", e.getMessage());
        }
    }
}