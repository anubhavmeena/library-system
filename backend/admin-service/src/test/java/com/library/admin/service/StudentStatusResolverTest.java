package com.library.admin.service;

import com.library.admin.entity.Membership;
import com.library.admin.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StudentStatusResolverTest {

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
        assertThat(StudentStatusResolver.resolve(m, null, null)).isEqualTo(StudentStatusResolver.Status.PAID);
    }

    @Test
    void resolve_activeZeroPending_isPaid() {
        Membership m = membership(Membership.Status.ACTIVE, LocalDate.now().plusDays(10));
        assertThat(StudentStatusResolver.resolve(m, payment(BigDecimal.ZERO), null))
                .isEqualTo(StudentStatusResolver.Status.PAID);
    }

    @Test
    void resolve_activeWithPendingBalance_isPending() {
        Membership m = membership(Membership.Status.ACTIVE, LocalDate.now().plusDays(10));
        assertThat(StudentStatusResolver.resolve(m, payment(new BigDecimal("200")), null))
                .isEqualTo(StudentStatusResolver.Status.PENDING);
    }

    @Test
    void resolve_graceWithinTenDays_isGrace() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(10));
        assertThat(StudentStatusResolver.resolve(m, null, null)).isEqualTo(StudentStatusResolver.Status.GRACE);
    }

    @Test
    void resolve_graceExactlyAtBoundary_isStillGrace() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(StudentStatusResolver.GRACE_DISPLAY_DAYS));
        assertThat(StudentStatusResolver.resolve(m, null, null)).isEqualTo(StudentStatusResolver.Status.GRACE);
    }

    @Test
    void resolve_graceOneDayPastBoundary_isExpired() {
        Membership m = membership(Membership.Status.GRACE, LocalDate.now().minusDays(StudentStatusResolver.GRACE_DISPLAY_DAYS + 1));
        assertThat(StudentStatusResolver.resolve(m, null, null)).isEqualTo(StudentStatusResolver.Status.EXPIRED);
    }

    @Test
    void resolve_noCurrentNoLatestEver_isNew() {
        assertThat(StudentStatusResolver.resolve(null, null, null)).isEqualTo(StudentStatusResolver.Status.NEW);
    }

    @Test
    void resolve_noCurrentLatestEverPending_isNew() {
        Membership latest = membership(Membership.Status.PENDING, LocalDate.now().plusDays(30));
        assertThat(StudentStatusResolver.resolve(null, null, latest)).isEqualTo(StudentStatusResolver.Status.NEW);
    }

    @Test
    void resolve_noCurrentLatestEverExpired_isReleased() {
        Membership latest = membership(Membership.Status.EXPIRED, LocalDate.now().minusDays(20));
        assertThat(StudentStatusResolver.resolve(null, null, latest)).isEqualTo(StudentStatusResolver.Status.RELEASED);
    }

    @Test
    void resolve_noCurrentLatestEverCancelled_isReleased() {
        Membership latest = membership(Membership.Status.CANCELLED, LocalDate.now().minusDays(20));
        assertThat(StudentStatusResolver.resolve(null, null, latest)).isEqualTo(StudentStatusResolver.Status.RELEASED);
    }

    @Test
    void resolve_noCurrentLatestEverUnexpectedQueued_degradesToNew() {
        // Near-impossible in practice (a QUEUED row always has an ACTIVE sibling),
        // but the resolver must degrade safely rather than throw.
        Membership latest = membership(Membership.Status.QUEUED, LocalDate.now().plusDays(30));
        assertThat(StudentStatusResolver.resolve(null, null, latest)).isEqualTo(StudentStatusResolver.Status.NEW);
    }
}
