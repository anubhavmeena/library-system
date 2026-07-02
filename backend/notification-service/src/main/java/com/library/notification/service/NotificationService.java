package com.library.notification.service;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.dto.SeatAssistanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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
                        "━━━━━━━━━━━━━━━━━━━━\n\n"                    +
                        "Please carry a valid ID on your first visit.\n\n" +
                        "📍 Happy studying! — Target Zone Library",
                name,
                e.getPlanName(),
                e.getSeatNumber(),
                formatShift(e.getShift()),
                e.getStartDate(),
                e.getEndDate(),
                e.getAmountPaid()
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