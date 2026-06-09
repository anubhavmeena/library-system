package com.library.membership.service;

import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.PaymentDto;
import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;
import com.library.membership.entity.Plan;
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

    @Mock  MembershipRepository membershipRepository;
    @Mock  PaymentRepository    paymentRepository;
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
}
