package com.library.notification.service;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.RenewalReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final WhatsAppService whatsAppService;
    private final EmailService    emailService;

    @Value("${notification.admin-email:admin@targetzone.co.in}")
    private String adminEmail;

    @Value("${notification.admin-whatsapp:}")
    private String adminWhatsapp;

    // ── Booking Confirmed ─────────────────────────────────────────────────────
    // Sends WhatsApp + email to student, and an alert to admin

    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        String whatsappMsg  = buildBookingWhatsApp(event);
        String emailSubject = "✅ Your Library Seat is Confirmed!";
        String emailBody    = buildBookingEmail(event);

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
                event.getUserName(),
                event.getSeatNumber(),
                event.getPlanName(),
                formatShift(event.getShift()),
                event.getAmountPaid()
        );

        if (hasValue(adminWhatsapp)) {
            whatsAppService.send(adminWhatsapp, adminMsg, null, "ADMIN_BOOKING_ALERT");
        }

        // Alert admin via email (always sent if adminEmail is configured)
        emailService.sendText(
                adminEmail,
                "New Booking — " + event.getUserName() + " | Seat " + event.getSeatNumber(),
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

        if (hasValue(adminWhatsapp)) {
            whatsAppService.send(adminWhatsapp, adminMsg, null, "ADMIN_REGISTRATION_ALERT");
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
                    event.getUserId(), "RENEWAL_REMINDER"
            );
        }

        log.info("Renewal reminder sent for user: {} ({} days left)",
                event.getUserId(), event.getDaysRemaining());
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

        log.info("Broadcast sent to user: {}", event.getUserId());
    }

    // ── Message Builders ──────────────────────────────────────────────────────

    private String buildBookingWhatsApp(BookingConfirmedEvent e) {
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
                        "━━━━━━━━━━━━━━━━━━━━\n\n"                    +
                        "Please carry a valid ID on your first visit.\n\n" +
                        "📍 Happy studying! — Target Zone Library",
                e.getUserName(),
                e.getPlanName(),
                e.getSeatNumber(),
                formatShift(e.getShift()),
                e.getStartDate(),
                e.getEndDate(),
                e.getAmountPaid()
        );
    }

    private String buildBookingEmail(BookingConfirmedEvent e) {
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
                        "Library Timings:\n"                                      +
                        "  Morning Shift : 6:00 AM – 2:00 PM\n"                  +
                        "  Evening Shift : 2:00 PM – 10:00 PM\n\n"               +
                        "Please carry a valid photo ID on your first visit.\n\n"  +
                        "Best regards,\n"                                         +
                        "Target Zone Library Team\n"                                    +
                        "https://targetzone.co.in",
                e.getUserName(),
                e.getPlanName(),
                e.getSeatNumber(),
                formatShift(e.getShift()),
                e.getStartDate(),
                e.getEndDate(),
                e.getAmountPaid()
        );
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