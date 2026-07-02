package com.library.membership.event;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {

    // ── User identity ─────────────────────────────────────────────────────────
    private String userId;
    private String membershipId;

    // ── User contact details ──────────────────────────────────────────────────
    // These are intentionally left null here and populated by notification-service
    // via a REST call to user-service using userId, avoiding coupling this service
    // to user-service at booking time.
    private String userName;
    private String userMobile;
    private String userEmail;

    // ── Booking details ───────────────────────────────────────────────────────
    private String planName;       // e.g. "Full Day Plan"
    private String planType;       // HALF_DAY | FULL_DAY
    private String seatNumber;     // e.g. "B14"
    private String shift;          // MORNING | EVENING | FULL_DAY
    private String startDate;      // yyyy-MM-dd
    private String endDate;        // yyyy-MM-dd
    private BigDecimal amountPaid;
    private String wifiName;
    private String wifiPassword;

    // ── Event metadata ────────────────────────────────────────────────────────
    private String eventType;      // BOOKING_CONFIRMED
}