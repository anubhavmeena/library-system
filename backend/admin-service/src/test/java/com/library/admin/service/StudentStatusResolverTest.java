package com.library.admin.service;

import com.library.admin.entity.Membership;
import com.library.admin.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StudentStatusResolverTest {

    private static final long GRACE_DAYS = 10L;

    private Membership membership(Membership.Status status, LocalDate endDate) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .status(status)
                .startDate(LocalDate.now().minusDays(30))
                .endDate(endDate)
                .build();
    }

    private Payment payment(BigDecimal pendingAmount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("999"))
                .pendingAmount(pendingAmount)
                .build();
    }

    @Test
    void resolve_activeNoPayment_isPaid() {
        Membership m = membership(Membership.Status.ACTIVE, LocalDate.now().plusDays(10));
        assertThat(StudentStatusResolver.resolve(m, null, null, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.PAID);
    }

    @Test
    void resolve_activeZeroPending_isPaid() {
        Membership m = membership(Membership.Status.ACTIVE, LocalDate.now().plusDays(10));
        assertThat(StudentStatusResolver.resolve(m, payment(BigDecimal.ZERO), null, GRACE_DAYS))
                .isEqualTo(StudentStatusResolver.Status.PAID);
    }

    @Test
    void resolve_activeWithPendingBalance_isPending() {
        Membership m = membership(Membership.Status.ACTIVE, LocalDate.now().plusDays(10));
        assertThat(StudentStatusResolver.resolve(m, payment(new BigDecimal("200")), null, GRACE_DAYS))
                .isEqualTo(StudentStatusResolver.Status.PENDING);
    }

    @Test
    void resolve_graceWithinTenDays_isGrace() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(10));
        assertThat(StudentStatusResolver.resolve(m, null, null, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.GRACE);
    }

    @Test
    void resolve_graceExactlyAtBoundary_isStillGrace() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(GRACE_DAYS));
        assertThat(StudentStatusResolver.resolve(m, null, null, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.GRACE);
    }

    @Test
    void resolve_graceOneDayPastBoundary_isExpired() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(GRACE_DAYS + 1));
        assertThat(StudentStatusResolver.resolve(m, null, null, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.EXPIRED);
    }

    @Test
    void resolve_graceCustomShorterThreshold_respectsConfiguredDays() {
        // With a 3-day configured grace period, 4 days overdue should already be EXPIRED
        // even though it'd still be GRACE under the old hardcoded 10-day default.
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(4));
        assertThat(StudentStatusResolver.resolve(m, null, null, 3L)).isEqualTo(StudentStatusResolver.Status.EXPIRED);
    }

    @Test
    void resolve_noCurrentNoLatestEver_isNew() {
        assertThat(StudentStatusResolver.resolve(null, null, null, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.NEW);
    }

    @Test
    void resolve_noCurrentLatestEverPending_isNew() {
        Membership latest = membership(Membership.Status.PENDING, LocalDate.now().plusDays(30));
        assertThat(StudentStatusResolver.resolve(null, null, latest, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.NEW);
    }

    @Test
    void resolve_noCurrentLatestEverExpired_isReleased() {
        Membership latest = membership(Membership.Status.EXPIRED, LocalDate.now().minusDays(20));
        assertThat(StudentStatusResolver.resolve(null, null, latest, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.RELEASED);
    }

    @Test
    void resolve_noCurrentLatestEverCancelled_isReleased() {
        Membership latest = membership(Membership.Status.CANCELLED, LocalDate.now().minusDays(20));
        assertThat(StudentStatusResolver.resolve(null, null, latest, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.RELEASED);
    }

    @Test
    void resolve_noCurrentLatestEverUnexpectedQueued_degradesToNew() {
        // Near-impossible in practice (a QUEUED row always has an ACTIVE sibling),
        // but the resolver must degrade safely rather than throw.
        Membership latest = membership(Membership.Status.QUEUED, LocalDate.now().plusDays(30));
        assertThat(StudentStatusResolver.resolve(null, null, latest, GRACE_DAYS)).isEqualTo(StudentStatusResolver.Status.NEW);
    }
}
