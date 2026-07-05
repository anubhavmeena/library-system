package com.library.notification.service;

import com.library.common.idcard.IdCardImageConverter;
import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.PaymentReceiptEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.dto.SeatAssistanceEvent;
import com.library.notification.entity.NotificationSetting;
import com.library.notification.repository.NotificationSettingRepository;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final WhatsAppService whatsAppService;
    private final EmailService    emailService;
    private final ReceiptPdfService receiptPdfService;
    private final StudentIdCardPdfService studentIdCardPdfService;
    private final RestTemplate    restTemplate;
    private final NotificationSettingRepository notificationSettingRepository;

    // Fail-open defaults (mirrors admin-service's NotificationSettingsService
    // catalog) — used only if admin-service hasn't lazily seeded a row for this
    // key yet, so a fresh deployment behaves exactly like it did before this
    // settings table existed rather than silently going dark.
    private static final Map<String, NotificationSetting> FAIL_OPEN_DEFAULTS = new LinkedHashMap<>();
    static {
        FAIL_OPEN_DEFAULTS.put("BOOKING_CONFIRMED",    defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("STUDENT_ID_CARD",      defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("USER_REGISTERED",      defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("RENEWAL_REMINDER",     defaultSetting(true, false));
        FAIL_OPEN_DEFAULTS.put("PENDING_FEE_REMINDER", defaultSetting(true, false));
        FAIL_OPEN_DEFAULTS.put("GRACE_DUES_REMINDER",  defaultSetting(true, false));
        FAIL_OPEN_DEFAULTS.put("MEMBERSHIP_GRACE",     defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("PENDING_FEE_CLEARED",  defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("PAYMENT_RECEIPT",      defaultSetting(true, true));
        FAIL_OPEN_DEFAULTS.put("ADMIN_BROADCAST",      defaultSetting(true, true));
    }

    private static NotificationSetting defaultSetting(boolean student, boolean admin) {
        return NotificationSetting.builder().sendToStudent(student).sendToAdmin(admin).hindiEnabled(false).build();
    }

    private NotificationSetting settingsFor(String key) {
        return notificationSettingRepository.findById(key).orElseGet(() -> FAIL_OPEN_DEFAULTS.get(key));
    }

    // Bilingual single message — appends the admin-provided Hindi translation
    // after the English text when the notification type has Hindi enabled and
    // a translation has actually been saved for this audience.
    private String withHindi(String englishText, NotificationSetting settings, String hindiText) {
        return settings.isHindiEnabled() && hasValue(hindiText) ? englishText + "\n\n" + hindiText : englishText;
    }

    @Value("${notification.admin-email:admin@targetzone.co.in}")
    private String adminEmail;

    @Value("${notification.admin-whatsapp:}")
    private String adminWhatsapp;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    @Value("${app.frontend-url:https://targetzone.co.in}")
    private String frontendUrl;

    // "payment_receipt" was created under plain English ("en"), not English (US)
    // ("en_US") like the notification template — Meta requires an exact
    // language-code match per template or the send fails with a
    // template-not-found error, so each document template gets its own
    // language property rather than sharing metaLanguage.
    @Value("${meta.whatsapp.receipt-template-name:payment_receipt}")
    private String receiptTemplateName;

    @Value("${meta.whatsapp.receipt-language:en}")
    private String receiptLanguage;

    // Image-header template ("id_card") — replaces the earlier document-header
    // "student_id_card" template for the WhatsApp send; the card still goes
    // out as an actual PDF for email and for the website's on-demand
    // download (IdCardService), only the WhatsApp copy is now an image.
    @Value("${meta.whatsapp.id-card-image-template-name:id_card}")
    private String idCardImageTemplateName;

    @Value("${meta.whatsapp.id-card-image-language:en}")
    private String idCardImageLanguage;

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
        NotificationSetting settings = settingsFor("BOOKING_CONFIRMED");
        String name         = hasValue(event.getUserName()) ? event.getUserName() : "Student";
        String whatsappMsg  = withHindi(buildBookingWhatsApp(event, name), settings, settings.getHindiTextStudent());
        String emailSubject = "✅ Your Library Seat is Confirmed!";
        String emailBody    = withHindi(buildBookingEmail(event, name), settings, settings.getHindiTextStudent());

        // Notify student via WhatsApp
        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), whatsappMsg,
                    event.getUserId(), "BOOKING_CONFIRMED"
            );
        }

        // Notify student via email
        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(), emailSubject, emailBody,
                    event.getUserId(), "BOOKING_CONFIRMED"
            );
        }

        // Alert admin via WhatsApp (if configured)
        String adminMsg = withHindi(String.format(
                "📚 New Booking!\n\n" +
                        "Student        : %s\n" +
                        "Seat           : %s\n" +
                        "Plan           : %s\n" +
                        "Shift          : %s\n" +
                        "Amount Paid    : ₹%.0f\n" +
                        "Amount Pending : ₹%.0f",
                name,
                event.getSeatNumber(),
                event.getPlanName(),
                formatShift(event.getShift()),
                event.getAmountPaid(),
                pendingOrZero(event)
        ), settings, settings.getHindiTextAdmin());

        if (settings.isSendToAdmin()) {
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
        }

        log.info("Booking confirmation notifications sent for user: {}", event.getUserId());

        sendStudentIdCard(event, name);
    }

    // ── Student ID Card (best-effort — must never block the notifications above,
    //    since a rendering/upload/Meta-template failure here is a separate
    //    concern from the plain-text booking confirmation the student is
    //    already relying on) ─────────────────────────────────────────────────
    private void sendStudentIdCard(BookingConfirmedEvent event, String name) {
        try {
            NotificationSetting settings = settingsFor("STUDENT_ID_CARD");
            byte[] photoBytes = fetchPhotoBytes(event.getPhotoUrl());
            byte[] pdf        = studentIdCardPdfService.buildIdCard(event, photoBytes);

            String idNumber       = buildIdNumber(event.getMembershipId());
            String attachmentName = idNumber + "_id_card.pdf";
            String validUpto      = formatDateDdMmYyyy(event.getEndDate());

            // WhatsApp gets an image (the "id_card" template has an image
            // header, not a document one) — rendered from the same PDF so the
            // design stays defined in exactly one place. Email and the
            // website's on-demand download both still use the PDF itself.
            // The Meta template is fixed/pre-approved, so Hindi translation
            // only applies to the email copy below, never this WhatsApp send.
            byte[] png          = IdCardImageConverter.toPng(pdf);
            String imageName    = idNumber + "_id_card.png";
            String idCardImageLink = uploadIdCardFile(event.getMembershipId(), png, imageName);

            if (idCardImageLink != null) {
                List<String> imageBodyParams = List.of(name);
                if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
                    whatsAppService.sendImageTemplate(event.getUserMobile(), idCardImageLink,
                            imageBodyParams, idCardImageTemplateName, idCardImageLanguage, event.getUserId(), "STUDENT_ID_CARD");
                }
                if (settings.isSendToAdmin()) {
                    for (String number : adminWhatsappNumbers()) {
                        whatsAppService.sendImageTemplate(number, idCardImageLink,
                                imageBodyParams, idCardImageTemplateName, idCardImageLanguage, null, "STUDENT_ID_CARD_ADMIN");
                    }
                }
            } else {
                log.warn("ID card image not hosted for membership {} — skipping WhatsApp send", event.getMembershipId());
            }

            if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
                emailService.sendWithAttachment(event.getUserEmail(), "Your Target Zone Library ID Card",
                        withHindi("Dear " + name + ",\n\nPlease find your library ID card attached.\n\nID No.: " + idNumber +
                                "\nValid Upto: " + validUpto + "\n\nTarget Zone Library Team", settings, settings.getHindiTextStudent()),
                        pdf, attachmentName, event.getUserId(), "STUDENT_ID_CARD");
            }
            if (settings.isSendToAdmin()) {
                emailService.sendWithAttachment(adminEmail, "Student ID Card — " + name,
                        withHindi("New ID card generated for " + name, settings, settings.getHindiTextAdmin()),
                        pdf, attachmentName, null, "STUDENT_ID_CARD_ADMIN");
            }

        } catch (Exception e) {
            log.warn("Student ID card generation/delivery failed for user {} (booking notifications already sent): {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    private byte[] fetchPhotoBytes(String photoUrl) {
        if (!hasValue(photoUrl)) return null;
        try {
            var resp = restTemplate.getForEntity(userServiceBaseUrl + photoUrl, byte[].class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) return resp.getBody();
        } catch (Exception e) {
            log.warn("Could not fetch photo from {} for ID card — proceeding without photo: {}", photoUrl, e.getMessage());
        }
        return null;
    }

    // Same convention membership-service's IdCardService.buildQrData() already
    // uses for its member ID — first 8 chars of the membership UUID, uppercased.
    private String buildIdNumber(String membershipId) {
        if (!hasValue(membershipId)) return "UNKNOWN";
        return membershipId.length() >= 8
                ? membershipId.substring(0, 8).toUpperCase()
                : membershipId.toUpperCase();
    }

    private String uploadIdCardFile(String membershipId, byte[] fileBytes, String filename) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("membershipId", membershipId);
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            var resp = restTemplate.exchange(
                    userServiceBaseUrl + "/api/users/internal/id-cards",
                    HttpMethod.POST, request, Map.class);

            if (resp.getBody() != null && resp.getBody().get("data") instanceof Map<?, ?> data) {
                Object url = data.get("idCardUrl");
                if (url != null) return frontendUrl + url;
            }
        } catch (Exception e) {
            log.warn("Could not host ID card file {} for membership {} — WhatsApp send will be skipped: {}",
                    filename, membershipId, e.getMessage());
        }
        return null;
    }

    // ── Welcome Notification (after registration) ─────────────────────────────

    public void sendWelcomeNotification(BookingConfirmedEvent event) {
        NotificationSetting settings = settingsFor("USER_REGISTERED");
        String msg = withHindi(String.format(
                "🎉 Welcome to Target Zone Library!\n\n"                                +
                        "Hi %s, your account has been created successfully.\n\n"          +
                        "You can now browse our membership plans and book your preferred " +
                        "seat.\n\n"                                                        +
                        "📚 Visit: https://targetzone.co.in\n\n"                          +
                        "Happy studying!",
                event.getUserName()
        ), settings, settings.getHindiTextStudent());

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), msg,
                    event.getUserId(), "USER_REGISTERED"
            );
        }

        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Welcome to Target Zone Library! 📚",
                    msg,
                    event.getUserId(),
                    "USER_REGISTERED"
            );
        }

        // Alert admin
        String adminMsg = withHindi(String.format(
                "🆕 New Student Registered!\n\nName   : %s\nMobile : %s\nEmail  : %s",
                event.getUserName(),
                hasValue(event.getUserMobile()) ? event.getUserMobile() : "—",
                hasValue(event.getUserEmail())  ? event.getUserEmail()  : "—"
        ), settings, settings.getHindiTextAdmin());

        if (settings.isSendToAdmin()) {
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
        }

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
        if ("GRACE_DUES_REMINDER".equals(event.getEventType())) {
            sendGraceDuesReminder(event);
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
        if ("SEAT_RELEASED_NONPAYMENT".equals(event.getEventType())) {
            sendSeatReleasedNonPaymentAlert(event);
            return;
        }

        // Escalate urgency label based on days remaining
        NotificationSetting settings = settingsFor("RENEWAL_REMINDER");
        String urgency = event.getDaysRemaining() <= 3 ? "⚠️ URGENT" : "⏰ Reminder";

        String whatsappMsg = withHindi(String.format(
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
        ), settings, settings.getHindiTextStudent());

        String emailSubject = urgency + ": Membership expiring in "
                + event.getDaysRemaining() + " day"
                + (event.getDaysRemaining() == 1 ? "" : "s");

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), whatsappMsg,
                    event.getUserId(), "RENEWAL_REMINDER"
            );
        }

        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
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
        NotificationSetting settings = settingsFor("PENDING_FEE_REMINDER");
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = withHindi(String.format(
                "💰 Pending Fee Reminder\n\n"                                         +
                        "Hi %s,\n\n"                                                         +
                        "You have a pending library fee of *%s*.\n\n"                        +
                        "Please visit the library or contact us to clear your dues.\n\n"     +
                        "📚 Target Zone Library Team",
                event.getUserName(), amount
        ), settings, settings.getHindiTextStudent());

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "PENDING_FEE_REMINDER");
        }
        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
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

    // ── Grace Period Dues Reminder ────────────────────────────────────────────
    // Manually triggered by an admin (POST /api/admin/reminders/grace-dues) for
    // students whose membership is already in GRACE with dues still owed —
    // distinct from PENDING_FEE_REMINDER (cash balance on an ACTIVE membership)
    // and from MEMBERSHIP_EXPIRED_GRACE (the one-time alert fired automatically
    // the moment a membership first enters grace).

    private void sendGraceDuesReminder(RenewalReminderEvent event) {
        NotificationSetting settings = settingsFor("GRACE_DUES_REMINDER");
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "the pending amount";

        String msg = withHindi(String.format(
                "⏳ Grace Period — Dues Reminder\n\n"                                    +
                        "Hi %s,\n\n"                                                            +
                        "Your membership is in its grace period and fees is overdue. "         +
                        "Your seat *%s* is being held for you — please clear *%s* "            +
                        "soon to avoid losing your seat.\n\n"                                  +
                        "🔗 Pay now: https://targetzone.co.in/student/membership\n\n"          +
                        "📚 Target Zone Library Team",
                event.getUserName(),
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                amount
        ), settings, settings.getHindiTextStudent());

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "GRACE_DUES_REMINDER");
        }
        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Grace Period Dues Reminder — " + amount,
                    msg,
                    adminEmail,
                    event.getUserId(), "GRACE_DUES_REMINDER"
            );
        }
        log.info("Grace dues reminder sent for user: {} ({})", event.getUserId(), amount);
    }

    // ── Pending Fee Cleared ───────────────────────────────────────────────────
    // Triggered by AdminService.clearPendingFees() — sent to both the student
    // (confirmation) and admin (audit trail), unlike the reminder above which is
    // student-facing only.

    private void sendPendingFeeClearedStudentAlert(RenewalReminderEvent event) {
        NotificationSetting settings = settingsFor("PENDING_FEE_CLEARED");
        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "your outstanding amount";

        String msg = withHindi(String.format(
                "✅ Pending Fee Cleared\n\n"                                          +
                        "Hi %s,\n\n"                                                         +
                        "Your pending library fee of *%s* has been cleared. Thank you!\n\n"  +
                        "📚 Target Zone Library Team",
                event.getUserName(), amount
        ), settings, settings.getHindiTextStudent());

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "PENDING_FEE_CLEARED");
        }
        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
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
        NotificationSetting settings = settingsFor("PENDING_FEE_CLEARED");
        if (!settings.isSendToAdmin()) return;

        String amount = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = withHindi(String.format(
                "✅ Pending Fee Cleared\n\nStudent: %s\nSeat   : %s\nAmount : %s",
                event.getUserName(),
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                amount
        ), settings, settings.getHindiTextAdmin());

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

    // ── Seat Released (Non-Payment) ───────────────────────────────────────────
    // Triggered by AdminMembershipService.releaseSeat() only when the admin
    // explicitly opts in via the confirmation dialog — a per-action choice, not
    // a persistent Notification Settings toggle, so there's no on/off gate here.

    private void sendSeatReleasedNonPaymentAlert(RenewalReminderEvent event) {
        String msg = String.format(
                "Hi %s, your seat %s has expired due to non-payment of dues and has been " +
                        "released by the admin. You are no longer a student at the library. " +
                        "Thanks you.\n\n-Admin",
                event.getUserName(),
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A"
        );

        if (hasValue(event.getUserMobile())) {
            whatsAppService.send(event.getUserMobile(), msg, event.getUserId(), "SEAT_RELEASED_NONPAYMENT");
        }
        if (hasValue(event.getUserEmail())) {
            emailService.sendText(
                    event.getUserEmail(),
                    "Your Library Seat Has Been Released",
                    msg,
                    adminEmail,
                    event.getUserId(), "SEAT_RELEASED_NONPAYMENT"
            );
        }
        log.info("Seat-released (non-payment) notification sent to user: {}", event.getUserId());
    }

    // ── Payment Receipt ───────────────────────────────────────────────────────
    // Triggered by membership-service (online payment) and admin-service (cash
    // payment / dues clearance) after a payment is confirmed. Generates a PDF,
    // hosts it via user-service (so it has a public URL Meta can fetch), sends
    // it as a real WhatsApp document attachment via the approved "payment_receipt"
    // template (to both student and admin), and emails it as a real attachment too.

    public void sendPaymentReceipt(PaymentReceiptEvent event) {
        NotificationSetting settings = settingsFor("PAYMENT_RECEIPT");
        byte[] pdf = receiptPdfService.buildReceipt(event);
        String attachmentName = (event.getInvoiceId() != null ? event.getInvoiceId() : "receipt") + ".pdf";
        String receiptLink = uploadReceiptPdf(event.getInvoiceId(), pdf, attachmentName);

        String pending = event.getAmountPending() != null && event.getAmountPending().signum() > 0
                ? "₹" + event.getAmountPending().stripTrailingZeros().toPlainString()
                : "₹0";
        String paid = event.getAmountPaid() != null
                ? "₹" + event.getAmountPaid().stripTrailingZeros().toPlainString()
                : "₹0";

        String validUptoLine = hasValue(event.getValidUpto())
                ? "Valid Upto  : " + event.getValidUpto() + "\n"
                : "";

        String emailSubject = "Payment Receipt — Invoice " + event.getInvoiceId();
        String emailBody = String.format(
                "Dear %s,\n\n"                                        +
                        "Please find attached your payment receipt.\n\n"     +
                        "Invoice No.  : %s\n"                                 +
                        "Date         : %s\n"                                 +
                        "Amount Paid  : %s\n"                                 +
                        "Amount Pending: %s\n"                                +
                        "%s\n"                                               +
                        "Best regards,\n"                                     +
                        "Target Zone Library Team",
                event.getUserName(), event.getInvoiceId(), event.getPaymentDate(), paid, pending, validUptoLine
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
            if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
                whatsAppService.sendDocumentTemplate(event.getUserMobile(), receiptLink, attachmentName,
                        bodyParams, receiptTemplateName, receiptLanguage, event.getUserId(), "PAYMENT_RECEIPT");
            }
            if (settings.isSendToAdmin()) {
                for (String number : adminWhatsappNumbers()) {
                    whatsAppService.sendDocumentTemplate(number, receiptLink, attachmentName,
                            bodyParams, receiptTemplateName, receiptLanguage, null, "PAYMENT_RECEIPT_ADMIN");
                }
            }
        } else {
            // Couldn't host the PDF — the document-header template requires a
            // link, so fall back to the plain text template (no attachment).
            String validUptoFallbackLine = hasValue(event.getValidUpto())
                    ? "\nValid Upto : " + event.getValidUpto()
                    : "";
            String fallbackMsg = String.format(
                    "🧾 Payment Receipt\n\nInvoice : %s\nDate    : %s\nPaid    : %s\nPending : %s%s\n\n📚 Target Zone Library",
                    event.getInvoiceId(), event.getPaymentDate(), paid, pending, validUptoFallbackLine
            );
            log.warn("Receipt PDF not hosted for invoice {} — WhatsApp falling back to text-only message", event.getInvoiceId());
            if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
                whatsAppService.send(event.getUserMobile(), fallbackMsg, event.getUserId(), "PAYMENT_RECEIPT");
            }
            if (settings.isSendToAdmin()) {
                for (String number : adminWhatsappNumbers()) {
                    whatsAppService.send(number, fallbackMsg, null, "PAYMENT_RECEIPT_ADMIN");
                }
            }
        }

        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
            emailService.sendWithAttachment(
                    event.getUserEmail(), emailSubject, withHindi(emailBody, settings, settings.getHindiTextStudent()),
                    pdf, attachmentName, event.getUserId(), "PAYMENT_RECEIPT");
        }
        if (settings.isSendToAdmin()) {
            emailService.sendWithAttachment(
                    adminEmail, emailSubject + " — " + event.getUserName(), withHindi(emailBody, settings, settings.getHindiTextAdmin()),
                    pdf, attachmentName, null, "PAYMENT_RECEIPT_ADMIN");
        }

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

        if (settingsFor("ADMIN_BROADCAST").isSendToStudent() && hasValue(event.getMobile())) {
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
        NotificationSetting settings = settingsFor("MEMBERSHIP_GRACE");
        if (!settings.isSendToAdmin()) return;

        String dues = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "an outstanding amount";

        String msg = withHindi(String.format(
                "🔒 Seat Held — Grace Period Started\n\n"                              +
                        "Seat   : %s\n"                                                        +
                        "Student: %s\n"                                                        +
                        "Expired: %s\n"                                                        +
                        "Dues   : %s\n\n"                                                       +
                        "The seat is NOT bookable — it stays assigned to this student "        +
                        "until you release it (Admin → Students → Actions → Release Seat).",
                event.getSeatNumber() != null ? event.getSeatNumber() : "N/A",
                event.getUserName(), event.getExpiryDate(), dues
        ), settings, settings.getHindiTextAdmin());

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
        NotificationSetting settings = settingsFor("MEMBERSHIP_GRACE");
        String dues = event.getPendingAmount() != null
                ? "₹" + event.getPendingAmount().stripTrailingZeros().toPlainString()
                : "the plan amount";

        String msg = withHindi(String.format(
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
        ), settings, settings.getHindiTextStudent());

        String emailSubject = "Your membership has expired — seat " +
                (event.getSeatNumber() != null ? event.getSeatNumber() : "") + " held for you";

        if (settings.isSendToStudent() && hasValue(event.getUserMobile())) {
            whatsAppService.send(
                    event.getUserMobile(), msg,
                    event.getUserId(), "MEMBERSHIP_EXPIRED_GRACE"
            );
        }

        if (settings.isSendToStudent() && hasValue(event.getUserEmail())) {
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
                        "📋 Plan    : %s\n"                           +
                        "💺 Seat    : %s\n"                           +
                        "⏰ Shift   : %s\n"                           +
                        "📅 From    : %s\n"                           +
                        "📅 To      : %s\n"                           +
                        "💰 Paid    : ₹%.0f\n"                        +
                        "⏳ Pending : ₹%.0f\n"                        +
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
                pendingOrZero(e),
                wifiWhatsAppBlock(e)
        );
    }

    private String buildBookingEmail(BookingConfirmedEvent e, String name) {
        return String.format(
                "Dear %s,\n\n"                                            +
                        "Your library membership has been confirmed!\n\n"         +
                        "MEMBERSHIP DETAILS\n"                                    +
                        "------------------\n"                                    +
                        "Plan           : %s\n"                                   +
                        "Seat Number    : %s\n"                                   +
                        "Shift          : %s\n"                                   +
                        "Start Date     : %s\n"                                   +
                        "End Date       : %s\n"                                   +
                        "Amount Paid    : ₹%.0f\n"                                +
                        "Amount Pending : ₹%.0f\n\n"                              +
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
                pendingOrZero(e),
                wifiEmailBlock(e)
        );
    }

    private BigDecimal pendingOrZero(BookingConfirmedEvent e) {
        return e.getPendingAmount() != null ? e.getPendingAmount() : BigDecimal.ZERO;
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