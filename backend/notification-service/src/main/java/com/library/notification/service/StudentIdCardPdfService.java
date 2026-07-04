package com.library.notification.service;

import com.library.common.idcard.IdCardData;
import com.library.common.idcard.IdCardIdGenerator;
import com.library.common.idcard.IdCardPdfGenerator;
import com.library.notification.dto.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Automatic, branded Target Zone Library ID card — built and sent on every
// booking-confirmed event (see NotificationService.sendStudentIdCard()).
// Renders through the shared OpenPDF-based generator in common-lib
// (IdCardPdfGenerator), the same one membership-service's on-demand
// IdCardService uses — both card designs were unified onto this one design.
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentIdCardPdfService {

    private final IdCardPdfGenerator idCardPdfGenerator;

    public byte[] buildIdCard(BookingConfirmedEvent event, byte[] photoBytes) {
        return idCardPdfGenerator.generate(toIdCardData(event, photoBytes));
    }

    private IdCardData toIdCardData(BookingConfirmedEvent event, byte[] photoBytes) {
        return new IdCardData(
                orDash(event.getUserName()),
                orDash(event.getUserMobile()),
                IdCardIdGenerator.shortId(event.getMembershipId()),
                event.getPlanName(),
                formatDateDdMmmYyyy(event.getEndDate()),
                photoBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String formatDateDdMmmYyyy(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "—";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
