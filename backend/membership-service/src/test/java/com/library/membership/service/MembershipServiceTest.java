package com.library.membership.service;

import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.PaymentDto;
import com.library.membership.entity.AppSettings;
import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;
import com.library.membership.entity.Plan;
import com.library.membership.repository.AppSettingsRepository;
import com.library.membership.repository.MembershipRepository;
import com.library.membership.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock  MembershipRepository  membershipRepository;
    @Mock  PaymentRepository     paymentRepository;
    @Mock  AppSettingsRepository appSettingsRepository;
    @InjectMocks MembershipService membershipService;

    private final String userId = UUID.randomUUID().toString();

    private Plan buildPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Full Day Plan")
                .planType(Plan.PlanType.FULL_DAY)
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    private Membership buildActiveMembership() {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .plan(buildPlan())
                .seatNumber("A1")
                .shift("FULL_DAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(Membership.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Payment buildPayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .membershipId(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .amount(BigDecimal.valueOf(600))
                .gatewayOrderId("order_test")
                .status(Payment.Status.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Membership buildGraceMembership(LocalDate endDate) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .plan(buildPlan())
                .seatNumber("A1")
                .shift("FULL_DAY")
                .startDate(endDate.minusDays(30))
                .endDate(endDate)
                .status(Membership.Status.GRACE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getUserActiveMembership ───────────────────────────────────────────────

    @Test
    void getUserActiveMembership_found_returnsMembershipDto() {
        Membership m = buildActiveMembership();
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId)))
                .thenReturn(Optional.of(m));

        MembershipDto result = membershipService.getUserActiveMembership(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(m.getId().toString());
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getUserActiveMembership_notFound_returnsNull() {
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId)))
                .thenReturn(Optional.empty());

        // Intentional null return — frontend shows "Get a plan" CTA
        assertThat(membershipService.getUserActiveMembership(userId)).isNull();
    }

    @Test
    void getUserActiveMembership_dtoFieldsPopulated() {
        Membership m = buildActiveMembership();
        when(membershipRepository.findActiveByUserId(any())).thenReturn(Optional.of(m));

        MembershipDto dto = membershipService.getUserActiveMembership(userId);

        assertThat(dto.getSeatNumber()).isEqualTo("A1");
        assertThat(dto.getShift()).isEqualTo("FULL_DAY");
        assertThat(dto.getPlanName()).isEqualTo("Full Day Plan");
    }

    // ── getUserMemberships ────────────────────────────────────────────────────

    @Test
    void getUserMemberships_returnsMappedDtos() {
        Membership m1 = buildActiveMembership();
        Membership m2 = buildActiveMembership();
        m2.setStatus(Membership.Status.EXPIRED);
        when(membershipRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId)))
                .thenReturn(List.of(m1, m2));

        List<MembershipDto> result = membershipService.getUserMemberships(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MembershipDto::getStatus)
                .containsExactlyInAnyOrder("ACTIVE", "EXPIRED");
    }

    @Test
    void getUserMemberships_empty_returnsEmptyList() {
        when(membershipRepository.findByUserIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        assertThat(membershipService.getUserMemberships(userId)).isEmpty();
    }

    @Test
    void getUserMemberships_passesCorrectUserIdToRepository() {
        when(membershipRepository.findByUserIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        membershipService.getUserMemberships(userId);

        verify(membershipRepository).findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
    }

    // ── getUserPayments ───────────────────────────────────────────────────────

    @Test
    void getUserPayments_returnsMappedDtos() {
        Payment p1 = buildPayment();
        Payment p2 = buildPayment();
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId)))
                .thenReturn(List.of(p1, p2));

        List<PaymentDto> result = membershipService.getUserPayments(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PaymentDto::getStatus)
                .containsOnly("SUCCESS");
    }

    @Test
    void getUserPayments_empty_returnsEmptyList() {
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        assertThat(membershipService.getUserPayments(userId)).isEmpty();
    }

    @Test
    void getUserPayments_passesCorrectUserIdToRepository() {
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        membershipService.getUserPayments(userId);

        verify(paymentRepository).findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
    }

    @Test
    void getUserPayments_excludesZeroAmountRows() {
        Payment zeroPaid = buildPayment();
        zeroPaid.setAmount(BigDecimal.ZERO);
        Payment realPaid = buildPayment();
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId)))
                .thenReturn(List.of(zeroPaid, realPaid));

        List<PaymentDto> result = membershipService.getUserPayments(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(realPaid.getId().toString());
    }

    // ── getMyDisplayStatus ────────────────────────────────────────────────────

    @Test
    void getMyDisplayStatus_activeNoPendingPayment_isPaid() {
        Membership m = buildActiveMembership();
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.of(m));
        Payment p = buildPayment();
        p.setPendingAmount(BigDecimal.ZERO);
        when(paymentRepository.findByMembershipId(m.getId())).thenReturn(Optional.of(p));
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("PAID");
    }

    @Test
    void getMyDisplayStatus_activeWithPendingPayment_isPending() {
        Membership m = buildActiveMembership();
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.of(m));
        Payment p = buildPayment();
        p.setPendingAmount(BigDecimal.valueOf(200));
        when(paymentRepository.findByMembershipId(m.getId())).thenReturn(Optional.of(p));
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("PENDING");
    }

    @Test
    void getMyDisplayStatus_graceWithinConfiguredWindow_isGrace() {
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        Membership grace = buildGraceMembership(LocalDate.now().minusDays(5));
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.of(grace));
        when(paymentRepository.findByMembershipId(grace.getId())).thenReturn(Optional.empty());
        AppSettings settings = AppSettings.builder().id(1L).graceDays(10).build();
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("GRACE");
    }

    @Test
    void getMyDisplayStatus_gracePastConfiguredWindow_isExpired() {
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        Membership grace = buildGraceMembership(LocalDate.now().minusDays(15));
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.of(grace));
        when(paymentRepository.findByMembershipId(grace.getId())).thenReturn(Optional.empty());
        AppSettings settings = AppSettings.builder().id(1L).graceDays(10).build();
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("EXPIRED");
    }

    @Test
    void getMyDisplayStatus_noCurrentLatestExpired_isReleased() {
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        Membership latest = buildActiveMembership();
        latest.setStatus(Membership.Status.EXPIRED);
        when(membershipRepository.findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(
                UUID.fromString(userId), Membership.Status.PENDING))
                .thenReturn(Optional.of(latest));
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("RELEASED");
    }

    @Test
    void getMyDisplayStatus_noCurrentOnlyAbandonedPendingHistory_isNewNotReleased() {
        // Regression: a student who retried a failed checkout leaves PENDING
        // rows behind. Those must never be picked as "latestEver" — an
        // abandoned attempt isn't real history, and letting it win here
        // previously misreported RELEASED students as NEW (or vice versa).
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(
                UUID.fromString(userId), Membership.Status.PENDING))
                .thenReturn(Optional.empty());
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("NEW");
    }

    @Test
    void getMyDisplayStatus_noCurrentNoHistory_isNew() {
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(
                UUID.fromString(userId), Membership.Status.PENDING))
                .thenReturn(Optional.empty());
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("NEW");
    }

    @Test
    void getMyDisplayStatus_missingGraceDaysSetting_fallsBackToDefault() {
        // No AppSettings row at all — DEFAULT_GRACE_DAYS (10) should apply,
        // so 5 days overdue stays GRACE rather than EXPIRED.
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        Membership grace = buildGraceMembership(LocalDate.now().minusDays(5));
        when(membershipRepository.findGraceByUserId(UUID.fromString(userId))).thenReturn(Optional.of(grace));
        when(paymentRepository.findByMembershipId(grace.getId())).thenReturn(Optional.empty());
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.getMyDisplayStatus(userId)).isEqualTo("GRACE");
    }
}
