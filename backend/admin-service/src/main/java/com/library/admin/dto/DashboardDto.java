package com.library.admin.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardDto {

    // ── Student stats ──────────────────────────────────────────────────────
    private long totalStudents;

    // ── Membership stats ───────────────────────────────────────────────────
    private long activeMemberships;
    private long expiredMemberships;
    private long expiringThisWeek;     // ACTIVE memberships expiring within 7 days

    // ── Seat stats ─────────────────────────────────────────────────────────
    private long totalSeats;           // Always 110
    private long occupiedSeats;        // = activeMemberships (1 seat per membership)
    private long availableSeats;       // = totalSeats - occupiedSeats

    // ── Revenue ────────────────────────────────────────────────────────────
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private long       paymentsThisMonth;

    // ── Visitors ───────────────────────────────────────────────────────────
    private long totalVisitors;
    private long visitorsToday;
}