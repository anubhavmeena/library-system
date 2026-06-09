package com.library.notification.service;

import com.library.notification.entity.NotificationLog;
import com.library.notification.repository.NotificationLogRepository;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final NotificationLogRepository logRepository;

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${notification.from-email:noreply@targetzone.co.in}")
    private String fromEmail;

    @Value("${notification.from-name:Target Zone Library}")
    private String fromName;

    /**
     * Send a plain-text email via SendGrid and persist a delivery log entry.
     *
     * @param to      Recipient email address
     * @param subject Email subject line
     * @param body    Plain-text message body
     * @param userId  Student UUID for audit log — nullable (e.g. admin alerts)
     * @param event   Event type label stored in notification_logs
     *                e.g. BOOKING_CONFIRMED, RENEWAL_REMINDER, USER_REGISTERED
     */
    public void sendText(String to, String subject, String body,
                         String userId, String event) {
        send(to, subject, body, null, userId, event);
    }

    /**
     * Send an HTML email via SendGrid and persist a delivery log entry.
     *
     * @param to       Recipient email address
     * @param subject  Email subject line
     * @param htmlBody HTML message body (inline CSS recommended for email clients)
     * @param userId   Student UUID for audit log — nullable
     * @param event    Event type label stored in notification_logs
     */
    public void sendHtml(String to, String subject, String htmlBody,
                         String userId, String event) {
        send(to, subject, null, htmlBody, userId, event);
    }

    // ── Internal send ─────────────────────────────────────────────────────────

    private void send(String to, String subject,
                      String textBody, String htmlBody,
                      String userId, String event) {

        DeliveryStatus status = DeliveryStatus.SENT;
        String errorMessage = null;
        // Use whichever body type was supplied — for logging purposes
        String logBody = textBody != null ? textBody : htmlBody;

        if (sendgridApiKey.isBlank()) {
            // Dev mode — log to console, no actual send
            log.info("[DEV] Email → {} | Subject: {} | Body:\n{}", to, subject, logBody);
        } else {
            try {
                Email   from    = new Email(fromEmail, fromName);
                Email   toEmail = new Email(to);
                Content content = (htmlBody != null)
                        ? new Content("text/html",  htmlBody)
                        : new Content("text/plain", textBody);

                Mail mail = new Mail(from, subject, toEmail, content);

                SendGrid sg  = new SendGrid(sendgridApiKey);
                Request  req = new Request();
                req.setMethod(Method.POST);
                req.setEndpoint("mail/send");
                req.setBody(mail.build());

                Response response = sg.api(req);

                if (response.getStatusCode() >= 400) {
                    // SendGrid returned an error HTTP status
                    status       = DeliveryStatus.FAILED;
                    errorMessage = "SendGrid HTTP "
                            + response.getStatusCode() + ": " + response.getBody();
                    log.error("Email send failed to {}: {}", to, errorMessage);
                } else {
                    log.info("Email sent to {} | Subject: {}", to, subject);
                }

            } catch (IOException e) {
                status       = DeliveryStatus.FAILED;
                errorMessage = e.getMessage();
                log.error("Email send exception to {}: {}", to, e.getMessage());
                // Do NOT rethrow — notification failure must not crash the Kafka consumer
            }
        }

        saveLog(userId, to, logBody, event, status, errorMessage);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Persists every send attempt to notification_logs — both successes and failures.
     * Message body is truncated to 1000 characters before saving to avoid
     * bloating the DB with full HTML email bodies.
     * Wrapped in try-catch so a DB write failure never prevents the attempt log
     * from blocking any downstream logic.
     */
    private void saveLog(String userId, String recipient, String message,
                         String event, DeliveryStatus status,
                         String errorMessage) {
        try {
            // Truncate long bodies (e.g. HTML emails) before persisting
            String truncated = (message != null)
                    ? message.substring(0, Math.min(message.length(), 1000))
                    : "";

            NotificationLog entry = NotificationLog.builder()
                    .userId(userId != null ? UUID.fromString(userId) : null)
                    .channel(Channel.EMAIL)
                    .event(event)
                    .recipient(recipient)
                    .message(truncated)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();

            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to save Email notification log: {}", e.getMessage());
        }
    }
}