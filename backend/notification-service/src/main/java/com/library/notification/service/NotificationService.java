package com.library.notification.service;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.PaymentReceiptEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.dto.SeatAssistanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final WhatsAppService whatsAppService;
    private final EmailService    emailService;
    private final ReceiptPdfService receiptPdfService;
    private final RestTemplate    restTemplate;

    @Value("${notification.admin-email:admin@targetzone.co.in}")
    private String adminEmail;

    @Value("${notification.admin-whatsapp:}")
    private String adminWhatsapp;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    @Value("${app.frontend-url:https://targetzone.co.in}")
    private String frontendUrl;

    private List<String> adminWhatsappNumbers() {
        if (!hasValue(adminWhatsapp)) return List.of();
        return Arrays.stream(adminWhatsapp.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // ── Booking Confirmed ─────────────────────────────────────────────────────
    // Sends WhatsApp + email to student, and an alert to admin

    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        String name         = hasValue(event.getUserName()) ? event.getUserName() : "Student";
        String whatsappMsg  = buildBookingWhatsApp(event, name);
        String emailSubject = "✅ Your Library Seat is Confirmed!";
        String emailBody    = buildBookingEmail(event, name);

        // Notify student via WhatsApp
        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), whatsappMsg,
                    event.getUserId(), "BOOKING_CONFIRMED"
            );
        }

        // Notify student via email
        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(), emailSubject, emailBody,
                    event.getUserId(), "BOOKING_CONFIRMED"
            );
        }

        // Alert admin via WhatsApp (if configured)
        String adminMsg = String.format(
                "📚 New Booking!\n\n" +
                        "Student : %s\n"     +
                        "Seat    : %s\n"     +
                        "Plan    : %s\n"     +
                        "Shift   : %s\n"     +
                        "Amount  : ₹%.0f",
                name,
                event.getSeatNumber(),
                event.getPlanName(),
                formatShift(event.getShift()),
                event.getAmountPaid()
        );

        for (String number : adminWhatsappNumbers()) {
            whatsAppService.send(number, adminMsg, null, "ADMIN_BOOKING_ALERT");
        }

        // Alert admin via email (always sent if adminEmail is configured)
        emailService.sendText(
                adminEmail,
                "New Booking — " + name + " | Seat " + event.getSeatNumber(),
                adminMsg,
                null,
                "ADMIN_BOOKING_ALERT"
        );

        log.info("Booking confirmation notifications sent for user: {}", event.getUserId());
    }

    // ── Welcome Notification (after registration) ─────────────────────────────

    public void sendWelcomeNotification(BookingConfirmedEvent event) {
        String msg = String.format(
                "🎉 Welcome to Target Zone Library!\n\n"                                +
                        "Hi %s, your account has been created successfully.\n\n"          +
                        "You can now browse our membership plans and book your preferred " +
                        "seat.\n\n"                                                        +
                        "📚 Visit: https://targetzone.co.in\n\n"                          +
                        "Happy studying!",
                event.getUserName()
        );

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), msg,
                    event.getUserId(), "USER_REGISTERED"
            );
        }

        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Welcome to Target Zone Library! 📚",
                    msg,
                    event.getUserId(),
                    "USER_REGISTERED"
            );
        }

        // Alert admin
        String adminMsg = String.format(
                "🆕 New Student Registered!\n\nName   : %s\nMobile : %s\nEmail  : %s",
                event.getUserName(),
                hasValue(event.getUserMobile()) ? event.getUserMobile() : "—",
                hasValue(event.getUserEmail())  ? event.getUserEmail()  : "—"
        );

        for (String number : adminWhatsappNumbers()) {
            whatsAppService.send(number, adminMsg, null, "ADMIN_REGISTRATION_ALERT");
        }

        emailService.sendText(
                adminEmail,
                "New Registration — " + event.getUserName(),
                adminMsg,
                null,
                "ADMIN_REGISTRATION_ALERT"
        );

        log.info("Welcome notification sent for user: {}", event.getUserId());
    }

    // ── Renewal Reminder ──────────────────────────────────────────────────────
    // Triggered at 7 days and 3 days before expiry by admin-service scheduler,
    // or manually via POST /api/admin/reminders/send

    public void sendRenewalReminder(RenewalReminderEvent event) {
        if ("SEAT_EXPIRED".equals(event.getEventType())) {
            sendSeatExpiredAlert(event);
            return;
        }
        if ("PENDING_FEE_REMINDER".equals(event.getEventType())) {
            sendPendingFeeReminder(event);
            return;
        }
        if ("MEMBERSHIP_GRACE_STARTED".equals(event.getEventType())) {
            sendGraceStartedAdminAlert(event);
            return;
        }
        if ("MEMBERSHIP_EXPIRED_GRACE".equals(event.getEventType())) {
            sendMembershipExpiredGraceAlert(event);
            return;
        }
        if ("PENDING_FEE_CLEARED".equals(event.getEventType())) {
            sendPendingFeeClearedStudentAlert(event);
            return;
        }
        if ("PENDING_FEE_CLEARED_ADMIN".equals(event.getEventType())) {
            sendPendingFeeClearedAdminAlert(event);
            return;
        }

        // Escalate urgency label based on days remaining
        String urgency = event.getDaysRemaining() <= 3 ? "⚠️ URGENT" : "⏰ Reminder";

        String whatsappMsg = String.format(
                "%s — Membership Expiring!\n\n"                                  +
                        "Hi %s,\n\n"                                                      +
                        "Your library membership expires on *%s* (%d day%s left).\n\n"   +
                        "Your reserved seat is *%s*. Renew now to keep it!\n\n"          +
                        "🔗 Renew: https://targetzone.co.in/student/membership\n\n"      +
                        "📚 Target Zone Library Team",
                urgency,
                event.getUserName(),
                event.getExpiryDate(),
                event.getDaysRemaining(),
                event.getDaysRemaining() == 1 ? "" : "s",
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A"
        );

        String emailSubject = urgency + ": Membership expiring in "
                + event.getDaysRemaining() + " day"
                + (event.getDaysRemaining() == 1 ? "" : "s");

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), whatsappMsg,
                    event.getUserId(), "RENEWAL_REMINDER"
            );
        }

        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(), emailSubject, whatsappMsg,
                    adminEmail,
                    event.getUserId(), "RENEWAL_REMINDER"
            );
        }

        log.info("Renewal reminder sent for user: {} ({} days left)",
                event.getUserId(), event.getDaysRemaining());
    }

    // ── Pending Fee Reminder ──────────────────────────────────────────────────

    private void sendPendingFeeReminder(RenewalReminderEvent event) {
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = String.format(
                "💰 Pending Fee Reminder\n\n"                                         +
                        "Hi %s,\n\n"                                                         +
                        "You have a pending library fee of *%s*.\n\n"                        +
                        "Please visit the library or contact us to clear your dues.\n\n"     +
                        "📚 Target Zone Library Team",
                event.getUserName(), amount
        );

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "PENDING_FEE_REMINDER");
        }
        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Pending Fee Reminder — " + amount,
                    msg,
                    adminEmail,
                    event.getUserId(), "PENDING_FEE_REMINDER"
            );
        }
        log.info("Pending fee reminder sent for user: {} ({})", event.getUserId(), amount);
    }

    // ── Pending Fee Cleared ───────────────────────────────────────────────────
    // Triggered by AdminService.clearPendingFees() — sent to both the student
    // (confirmation) and admin (audit trail), unlike the reminder above which is
    // student-facing only.

    private void sendPendingFeeClearedStudentAlert(RenewalReminderEvent event) {
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "your outstanding amount";

        String msg = String.format(
                "✅ Pending Fee Cleared\n\n"                                          +
                        "Hi %s,\n\n"                                                         +
                        "Your pending library fee of *%s* has been cleared. Thank you!\n\n"  +
                        "📚 Target Zone Library Team",
                event.getUserName(), amount
        );

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "PENDING_FEE_CLEARED");
        }
        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Pending Fee Cleared — " + amount,
                    msg,
                    adminEmail,
                    event.getUserId(), "PENDING_FEE_CLEARED"
            );
        }
        log.info("Pending fee cleared confirmation sent to user: {} ({})", event.getUserId(), amount);
    }

    private void sendPendingFeeClearedAdminAlert(RenewalReminderEvent event) {
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = String.format(
                "✅ Pending Fee Cleared\n\nStudent: %s\nSeat   : %s\nAmount : %s",
                event.getUserName(),
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                amount
        );

        for (String number : adminWhatsappNumbers()) {
            whatsAppService.send(number, msg, null, "PENDING_FEE_CLEARED_ADMIN");
        }

        emailService.sendText(
                adminEmail,
                "Pending Fee Cleared — " + event.getUserName() + " (" + amount + ")",
                msg,
                null,
                "PENDING_FEE_CLEARED_ADMIN"
        );

        log.info("Pending fee cleared alert sent to admin for user: {} ({})", event.getUserName(), amount);
    }

    // ── Payment Receipt ───────────────────────────────────────────────────────
    // Triggered by membership-service (online payment) and admin-service (cash
    // payment / dues clearance) after a payment is confirmed. Generates a PDF,
    // hosts it via user-service (so a link is available if ever needed), sends a
    // WhatsApp notification via the approved "payment_receipt" template to both
    // student and admin, and emails the PDF as a real attachment. NOTE: the
    // WhatsApp copy is text-only, NOT a real document attachment — the
    // "payment_receipt" template's actual approved header is static TEXT, not a
    // Document-media header (see WhatsAppService.sendDocumentTemplate's comment
    // for the full story) — only the email carries the actual PDF file.

    public void sendPaymentReceipt(PaymentReceiptEvent event) {
        byte[] pdf = receiptPdfService.buildReceipt(event);
        String attachmentName = (event.getInvoiceId() != null ? event.getInvoiceId() : "receipt") + ".pdf";
        String receiptLink = uploadReceiptPdf(event.getInvoiceId(), pdf, attachmentName);

        String pending = event.getAmountPending() != null && event.getAmountPending().signum() > 0
                ? "₹" + event.getAmountPending().stripTrailingZeros().toPlainString()
                : "₹0";
        String paid = event.getAmountPaid() != null
                ? "₹" + event.getAmountPaid().stripTrailingZeros().toPlainString()
                : "₹0";

        String emailSubject = "Payment Receipt — Invoice " + event.getInvoiceId();
        String emailBody = String.format(
                "Dear %s,\n\n"                                        +
                        "Please find attached your payment receipt.\n\n"     +
                        "Invoice No.  : %s\n"                                 +
                        "Date         : %s\n"                                 +
                        "Amount Paid  : %s\n"                                 +
                        "Amount Pending: %s\n\n"                              +
                        "Best regards,\n"                                     +
                        "Target Zone Library Team",
                event.getUserName(), event.getInvoiceId(), event.getPaymentDate(), paid, pending
        );

        // "payment_receipt" template's {{1}}..{{5}} order: name, amount paid
        // (numeric only — the template body already prints the ₹ symbol),
        // invoice/receipt number, payment date (DD/MM/YYYY), pending amount.
        List<String> bodyParams = List.of(
                hasValue(event.getUserName()) ? event.getUserName() : "Student",
                event.getAmountPaid() != null ? event.getAmountPaid().stripTrailingZeros().toPlainString() : "0",
                event.getInvoiceId() != null ? event.getInvoiceId() : "",
                formatDateDdMmYyyy(event.getPaymentDate()),
                event.getAmountPending() != null ? event.getAmountPending().stripTrailingZeros().toPlainString() : "0"
        );

        if (receiptLink != null) {
            if (hasValue(event.getUserMobile())) {
                whatsAppService.sendDocumentTemplate(event.getUserMobile(), receiptLink, attachmentName,
                        bodyParams, event.getUserId(), "PAYMENT_RECEIPT");
            }
            for (String number : adminWhatsappNumbers()) {
                whatsAppService.sendDocumentTemplate(number, receiptLink, attachmentName,
                        bodyParams, null, "PAYMENT_RECEIPT_ADMIN");
            }
        } else {
            // Couldn't host the PDF — the document-header template requires a
            // link, so fall back to the plain text template (no attachment).
            String fallbackMsg = String.format(
                    "🧾 Payment Receipt\n\nInvoice : %s\nDate    : %s\nPaid    : %s\nPending : %s\n\n📚 Target Zone Library",
                    event.getInvoiceId(), event.getPaymentDate(), paid, pending
            );
            log.warn("Receipt PDF not hosted for invoice {} — WhatsApp falling back to text-only message", event.getInvoiceId());
            if (hasValue(event.getUserMobile())) {
                whatsAppService.send(event.getUserMobile(), fallbackMsg, event.getUserId(), "PAYMENT_RECEIPT");
            }
            for (String number : adminWhatsappNumbers()) {
                whatsAppService.send(number, fallbackMsg, null, "PAYMENT_RECEIPT_ADMIN");
            }
        }

        if (hasValue(event.getUserEmail())) {
            emailService.sendWithAttachment(
                    event.getUserEmail(), emailSubject, emailBody, pdf, attachmentName,
                    event.getUserId(), "PAYMENT_RECEIPT");
        }
        emailService.sendWithAttachment(
                adminEmail, emailSubject + " — " + event.getUserName(), emailBody, pdf, attachmentName,
                null, "PAYMENT_RECEIPT_ADMIN");

        log.info("Payment receipt sent for user: {} invoice: {}", event.getUserId(), event.getInvoiceId());
    }

    // event.getPaymentDate() is yyyy-MM-dd (ISO); the approved template's sample
    // uses DD/MM/YYYY, so reformat for display consistency with what Meta approved.
    private String formatDateDdMmYyyy(String isoDate) {
        if (!hasValue(isoDate)) return "";
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(isoDate);
            return d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return isoDate;
        }
    }

    private String uploadReceiptPdf(String invoiceId, byte[] pdf, String filename) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("invoiceId", invoiceId);
            body.add("file", new ByteArrayResource(pdf) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            var resp = restTemplate.exchange(
                    userServiceBaseUrl + "/api/users/internal/receipts",
                    HttpMethod.POST, request, Map.class);

            if (resp.getBody() != null && resp.getBody().get("data") instanceof Map<?, ?> data) {
                Object url = data.get("receiptUrl");
                if (url != null) return frontendUrl + url;
            }
        } catch (Exception e) {
            log.warn("Could not host receipt PDF for invoice {} — WhatsApp message will omit the link: {}",
                    invoiceId, e.getMessage());
        }
        return null;
    }

    // ── Broadcast (admin → all active members) ────────────────────────────────

    public void sendBroadcast(BroadcastNotificationEvent event) {
        String msg = String.format(
                "📢 *Target Zone Library*\n\n%s\n\n— Library Management",
                event.getMessage()
        );

        if (hasValue(event.getMobile())) {
            whatsAppService.send(
                    event.getMobile(), msg,
                    event.getUserId(), "ADMIN_BROADCAST"
            );
        }

        if (event.isFirst()) {
            String echoMsg = String.format("📢 Broadcast sent!\n\nMessage: %s", event.getMessage());
            for (String number : adminWhatsappNumbers()) {
                whatsAppService.send(number, echoMsg, null, "ADMIN_BROADCAST_ECHO");
            }
            emailService.sendText(
                    adminEmail,
                    "Broadcast Sent — " + event.getMessage().substring(0, Math.min(50, event.getMessage().length())),
                    echoMsg,
                    null,
                    "ADMIN_BROADCAST_ECHO"
            );
        }

        log.info("Broadcast sent to user: {}", event.getUserId());
    }

    // ── Seat Assistance Admin Alert ───────────────────────────────────────────
    // Sent when a student taps "Call Admin" from the Contact Admin page

    public void sendSeatAssistanceAlert(SeatAssistanceEvent event) {
        String msg = String.format(
                "🙋 Student needs help at their seat!\n\nName : %s\nSeat : %s",
                event.getUserName(),
                event.getSeatNumber()
        );

        // Send to admin's registered mobile (from DB via event payload)
        if (hasValue(event.getAdminMobile())) {
            whatsAppService.send(event.getAdminMobile(), msg, null, "SEAT_ASSISTANCE");
        }
        // Also send to any additional numbers configured in ADMIN_WHATSAPP env var
        for (String number : adminWhatsappNumbers()) {
            if (!number.equals(event.getAdminMobile())) {
                whatsAppService.send(number, msg, null, "SEAT_ASSISTANCE");
            }
        }

        emailService.sendText(
                adminEmail,
                "🙋 Seat Assistance — " + event.getUserName() + " at Seat " + event.getSeatNumber(),
                msg,
                null,
                "SEAT_ASSISTANCE"
        );

        log.info("Seat assistance alert sent to admin for user: {} seat: {}",
                event.getUserId(), event.getSeatNumber());
    }

    // ── Seat Expired Admin Alert ──────────────────────────────────────────────

    private void sendSeatExpiredAlert(RenewalReminderEvent event) {
        String msg = String.format(
                "🪑 Seat Now Available!\n\nSeat   : %s\nStudent: %s\nExpired: %s\n\nThe seat is free for new booking.",
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                event.getUserName(),
                event.getExpiryDate()
        );

        for (String number : adminWhatsappNumbers()) {
            whatsAppService.send(number, msg, null, "SEAT_EXPIRED");
        }

        emailService.sendText(
                adminEmail,
                "Seat " + (event.getSeatNumber() != null ? event.getSeatNumber() : "N/A")
                        + " now free — " + event.getUserName() + "'s membership expired",
                msg,
                null,
                "SEAT_EXPIRED"
        );

        log.info("Seat expired alert sent to admin for seat {} (user: {})",
                event.getSeatNumber(), event.getUserName());
    }

    // ── Membership Grace Period ──────────────────────────────────────────────
    // Triggered when a membership's endDate passes with no queued renewal.
    // Unlike sendSeatExpiredAlert, the seat is NOT free — it's held for the
    // student during the grace period and only released by an explicit admin action.

    private void sendGraceStartedAdminAlert(RenewalReminderEvent event) {
        String dues = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = String.format(
                "🔒 Seat Held — Grace Period Started\n\n"                              +
                        "Seat   : %s\n"                                                        +
                        "Student: %s\n"                                                        +
                        "Expired: %s\n"                                                        +
                        "Dues   : %s\n\n"                                                       +
                        "The seat is NOT bookable — it stays assigned to this student "        +
                        "until you release it (Admin → Students → Actions → Release Seat).",
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                event.getUserName(), event.getExpiryDate(), dues
        );

        for (String number : adminWhatsappNumbers()) {
            whatsAppService.send(number, msg, null, "MEMBERSHIP_GRACE_STARTED");
        }

        emailService.sendText(
                adminEmail,
                "Seat " + (event.getSeatNumber() != null ? event.getSeatNumber() : "N/A")
                        + " held in grace — " + event.getUserName() + "'s membership expired",
                msg,
                null,
                "MEMBERSHIP_GRACE_STARTED"
        );

        log.info("Grace-started alert sent to admin for seat {} (user: {})",
                event.getSeatNumber(), event.getUserName());
    }

    private void sendMembershipExpiredGraceAlert(RenewalReminderEvent event) {
        String dues = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "the plan amount";

        String msg = String.format(
                "⚠️ Your Membership Has Expired\n\n"                                     +
                        "Hi %s,\n\n"                                                             +
                        "Your library membership expired on *%s*.\n\n"                          +
                        "We're holding your seat *%s* for you, but a payment of *%s* is "       +
                        "due to continue your plan. Please clear this amount soon — your "      +
                        "seat may be released by the library if it remains unpaid.\n\n"         +
                        "🔗 Pay now: https://targetzone.co.in/student/membership\n\n"           +
                        "📚 Target Zone Library Team",
                event.getUserName(), event.getExpiryDate(),
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A", dues
        );

        String emailSubject = "Your membership has expired — seat " +
                (event.getSeatNumber() != null ? event.getSeatNumber() : "") + " held for you";

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), msg,
                    event.getUserId(), "MEMBERSHIP_EXPIRED_GRACE"
            );
        }

        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(), emailSubject, msg,
                    adminEmail,
                    event.getUserId(), "MEMBERSHIP_EXPIRED_GRACE"
            );
        }

        log.info("Membership-expired-grace alert sent to user: {}", event.getUserId());
    }

    // ── Message Builders ──────────────────────────────────────────────────────

    private String buildBookingWhatsApp(BookingConfirmedEvent e, String name) {
        return String.format(
                "✅ Booking Confirmed!\n\n"                   +
                        "Hi *%s*,\n\n"                                +
                        "📚 *Target Zone Library — Membership Details*\n"   +
                        "━━━━━━━━━━━━━━━━━━━━\n"                      +
                        "📋 Plan   : %s\n"                            +
                        "💺 Seat   : %s\n"                            +
                        "⏰ Shift  : %s\n"                            +
                        "📅 From   : %s\n"                            +
                        "📅 To     : %s\n"                            +
                        "💰 Paid   : ₹%.0f\n"                         +
                        "%s"                                          +
                        "━━━━━━━━━━━━━━━━━━━━\n\n"                    +
                        "Please carry a valid ID on your first visit.\n\n" +
                        "📍 Happy studying! — Target Zone Library",
                name,
                e.getPlanName(),
                e.getSeatNumber(),
                formatShift(e.getShift()),
                e.getStartDate(),
                e.getEndDate(),
                e.getAmountPaid(),
                wifiWhatsAppBlock(e)
        );
    }

    private String buildBookingEmail(BookingConfirmedEvent e, String name) {
        return String.format(
                "Dear %s,\n\n"                                            +
                        "Your library membership has been confirmed!\n\n"         +
                        "MEMBERSHIP DETAILS\n"                                    +
                        "------------------\n"                                    +
                        "Plan        : %s\n"                                      +
                        "Seat Number : %s\n"                                      +
                        "Shift       : %s\n"                                      +
                        "Start Date  : %s\n"                                      +
                        "End Date    : %s\n"                                      +
                        "Amount Paid : ₹%.0f\n\n"                                 +
                        "%s"                                                      +
                        "Library Timings:\n"                                      +
                        "  Morning Shift : 6:00 AM – 2:00 PM\n"                  +
                        "  Evening Shift : 2:00 PM – 10:00 PM\n\n"               +
                        "Please carry a valid photo ID on your first visit.\n\n"  +
                        "Best regards,\n"                                         +
                        "Target Zone Library Team\n"                                    +
                        "https://targetzone.co.in",
                name,
                e.getPlanName(),
                e.getSeatNumber(),
                formatShift(e.getShift()),
                e.getStartDate(),
                e.getEndDate(),
                e.getAmountPaid(),
                wifiEmailBlock(e)
        );
    }

    // Both blank → admin hasn't configured WiFi yet, omit the block entirely
    // rather than printing an empty "WiFi Name: / Password:" line.
    private String wifiWhatsAppBlock(BookingConfirmedEvent e) {
        if (!hasValue(e.getWifiName()) && !hasValue(e.getWifiPassword())) return "";
        return String.format("📶 WiFi   : %s\n🔑 Password: %s\n",
                hasValue(e.getWifiName())     ? e.getWifiName()     : "N/A",
                hasValue(e.getWifiPassword()) ? e.getWifiPassword() : "N/A");
    }

    private String wifiEmailBlock(BookingConfirmedEvent e) {
        if (!hasValue(e.getWifiName()) && !hasValue(e.getWifiPassword())) return "";
        return String.format("WiFi Name     : %s\nWiFi Password : %s\n\n",
                hasValue(e.getWifiName())     ? e.getWifiName()     : "N/A",
                hasValue(e.getWifiPassword()) ? e.getWifiPassword() : "N/A");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatShift(String shift) {
        if (shift == null) return "Full Day (6AM–10PM)";
        return switch (shift.toUpperCase()) {
            case "MORNING" -> "Morning (6AM–2PM)";
            case "EVENING" -> "Evening (2PM–10PM)";
            default        -> "Full Day (6AM–10PM)";
        };
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}