package com.library.notification.service;

import com.library.notification.entity.NotificationLog;
import com.library.notification.repository.NotificationLogRepository;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final NotificationLogRepository logRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${notification.from-email:noreply@targetzone.co.in}")
    private String fromEmail;

    @Value("${notification.from-name:Target Zone Library}")
    private String fromName;

    public void sendText(String to, String subject, String body,
                         String userId, String event) {
        send(to, subject, body, null, null, userId, event);
    }

    public void sendText(String to, String subject, String body, String bcc,
                         String userId, String event) {
        send(to, subject, body, null, bcc, userId, event);
    }

    public void sendHtml(String to, String subject, String htmlBody,
                         String userId, String event) {
        send(to, subject, null, htmlBody, null, userId, event);
    }

    public void sendHtml(String to, String subject, String htmlBody, String bcc,
                         String userId, String event) {
        send(to, subject, null, htmlBody, bcc, userId, event);
    }

    public void sendWithAttachment(String to, String subject, String body,
                                   byte[] attachmentBytes, String attachmentFilename,
                                   String userId, String event) {
        String logBody = body;

        // Dev mode: nothing configured → log to console only, no attachment sent
        if (smtpHost.isBlank() && sendgridApiKey.isBlank()) {
            log.info("[DEV] Email → {} | Subject: {} | Attachment: {} | Body:\n{}",
                    to, subject, attachmentFilename, logBody);
            saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
            return;
        }

        // 1. Try local SMTP (Postfix)
        if (!smtpHost.isBlank() && mailSender != null) {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(fromEmail, fromName);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body, false);
                helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes));
                mailSender.send(msg);
                log.info("Email with attachment sent via SMTP to {} | Subject: {}", to, subject);
                saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
                return;
            } catch (Exception e) {
                log.warn("SMTP send (with attachment) failed to {}, falling back to SendGrid: {}", to, e.getMessage());
            }
        }

        // 2. Fallback: SendGrid REST API
        if (sendgridApiKey.isBlank()) {
            log.info("[DEV] Email (no SendGrid key) → {} | Subject: {} | Attachment: {}", to, subject, attachmentFilename);
            saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
            return;
        }

        DeliveryStatus status = DeliveryStatus.SENT;
        String errorMessage = null;

        try {
            Email   from    = new Email(fromEmail, fromName);
            Email   toEmail = new Email(to);
            Content content = new Content("text/plain", body);

            Mail mail = new Mail(from, subject, toEmail, content);

            Attachments attachment = new Attachments();
            attachment.setContent(Base64.getEncoder().encodeToString(attachmentBytes));
            attachment.setType("application/pdf");
            attachment.setFilename(attachmentFilename);
            attachment.setDisposition("attachment");
            mail.addAttachments(attachment);

            SendGrid sg  = new SendGrid(sendgridApiKey);
            Request  req = new Request();
            req.setMethod(Method.POST);
            req.setEndpoint("mail/send");
            req.setBody(mail.build());

            Response response = sg.api(req);

            if (response.getStatusCode() >= 400) {
                status       = DeliveryStatus.FAILED;
                errorMessage = "SendGrid HTTP " + response.getStatusCode() + ": " + response.getBody();
                log.error("Email with attachment send failed to {}: {}", to, errorMessage);
            } else {
                log.info("Email with attachment sent via SendGrid to {} | Subject: {}", to, subject);
            }

        } catch (IOException e) {
            status       = DeliveryStatus.FAILED;
            errorMessage = e.getMessage();
            log.error("Email with attachment send exception to {}: {}", to, e.getMessage());
        }

        saveLog(userId, to, logBody, event, status, errorMessage);
    }

    // ── Internal send ─────────────────────────────────────────────────────────

    private void send(String to, String subject,
                      String textBody, String htmlBody, String bcc,
                      String userId, String event) {

        String logBody = textBody != null ? textBody : htmlBody;

        // Dev mode: nothing configured → log to console only
        if (smtpHost.isBlank() && sendgridApiKey.isBlank()) {
            log.info("[DEV] Email → {} | Subject: {} | BCC: {} | Body:\n{}", to, subject, bcc, logBody);
            saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
            return;
        }

        // 1. Try local SMTP (Postfix)
        if (!smtpHost.isBlank() && mailSender != null) {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(fromEmail, fromName);
                helper.setTo(to);
                helper.setSubject(subject);
                if (bcc != null && !bcc.isBlank()) {
                    helper.addBcc(bcc);
                }
                if (htmlBody != null) {
                    helper.setText(htmlBody, true);
                } else {
                    helper.setText(textBody != null ? textBody : "", false);
                }
                mailSender.send(msg);
                log.info("Email sent via SMTP to {} | Subject: {}", to, subject);
                saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
                return;
            } catch (Exception e) {
                log.warn("SMTP send failed to {}, falling back to SendGrid: {}", to, e.getMessage());
            }
        }

        // 2. Fallback: SendGrid REST API
        if (sendgridApiKey.isBlank()) {
            log.info("[DEV] Email (no SendGrid key) → {} | Subject: {}", to, subject);
            saveLog(userId, to, logBody, event, DeliveryStatus.SENT, null);
            return;
        }

        DeliveryStatus status = DeliveryStatus.SENT;
        String errorMessage = null;

        try {
            Email   from    = new Email(fromEmail, fromName);
            Email   toEmail = new Email(to);
            Content content = (htmlBody != null)
                    ? new Content("text/html",  htmlBody)
                    : new Content("text/plain", textBody);

            Mail mail = new Mail(from, subject, toEmail, content);
            if (bcc != null && !bcc.isBlank()) {
                mail.getPersonalization().get(0).addBcc(new Email(bcc));
            }

            SendGrid sg  = new SendGrid(sendgridApiKey);
            Request  req = new Request();
            req.setMethod(Method.POST);
            req.setEndpoint("mail/send");
            req.setBody(mail.build());

            Response response = sg.api(req);

            if (response.getStatusCode() >= 400) {
                status       = DeliveryStatus.FAILED;
                errorMessage = "SendGrid HTTP "
                        + response.getStatusCode() + ": " + response.getBody();
                log.error("Email send failed to {}: {}", to, errorMessage);
            } else {
                log.info("Email sent via SendGrid to {} | Subject: {}", to, subject);
            }

        } catch (IOException e) {
            status       = DeliveryStatus.FAILED;
            errorMessage = e.getMessage();
            log.error("Email send exception to {}: {}", to, e.getMessage());
        }

        saveLog(userId, to, logBody, event, status, errorMessage);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveLog(String userId, String recipient, String message,
                         String event, DeliveryStatus status,
                         String errorMessage) {
        try {
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
