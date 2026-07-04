package com.library.membership.service;

import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Computes the student-facing membership status shown on the dashboard.
 * Purely display-driven — it never mutates DB state and introduces no new
 * Membership.Status enum value. This is a deliberate duplicate of
 * admin-service's identically-named resolver
 * (admin-service/src/main/java/com/library/admin/service/StudentStatusResolver.java),
 * which computes the same status for the admin Students list — keep the two
 * in sync if this rule ever changes. GRACE splits into GRACE/EXPIRED based on
 * how many days overdue the membership is; ACTIVE splits into PAID/PENDING
 * based on whether the linked Payment still carries a pending cash balance.
 */
public final class StudentStatusResolver {

    public enum Status { NEW, PAID, PENDING, GRACE, EXPIRED, RELEASED }

    private StudentStatusResolver() {}

    /**
     * @param current        the student's current membership (ACTIVE-not-past-due or GRACE), or null
     * @param currentPayment the Payment linked to {@code current}, or null — ignored when current is null
     * @param latestEver     the student's latest membership row of any status — only consulted when
     *                       current is null; pass null when current is non-null
     * @param graceDays      the admin-configured grace-period length (AppSettings.graceDays) — the
     *                       display-only threshold splitting GRACE into "Grace" vs "Expired" labels.
     *                       No DB enum change and no scheduler enforcement — GRACE stays open-ended
     *                       in the DB until an admin calls AdminMembershipService.releaseSeat().
     */
    public static Status resolve(Membership current, Payment currentPayment, Membership latestEver, long graceDays) {
        if (current != null) {
            if (current.getStatus() == Membership.Status.GRACE) {
                long daysOverdue = ChronoUnit.DAYS.between(current.getEndDate(), LocalDate.now());
                return daysOverdue > graceDays ? Status.EXPIRED : Status.GRACE;
            }
            boolean hasPendingBalance = currentPayment != null
                    && currentPayment.getPendingAmount() != null
                    && currentPayment.getPendingAmount().compareTo(BigDecimal.ZERO) > 0;
            return hasPendingBalance ? Status.PENDING : Status.PAID;
        }

        if (latestEver != null
                && (latestEver.getStatus() == Membership.Status.EXPIRED
                    || latestEver.getStatus() == Membership.Status.CANCELLED)) {
            return Status.RELEASED;
        }

        // No membership ever, an abandoned PENDING checkout, or any other
        // unexpected latest status — degrade safely to NEW.
        return Status.NEW;
    }
}
