package com.library.admin.service;

import com.library.admin.entity.Membership;
import com.library.admin.entity.SeatBooking;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Scoped to AdminMembershipService.releaseSeat() — the method changed to allow
// releasing an ACTIVE (not just GRACE) membership's seat. Other methods on this
// service (createCashMembership, changeSeat, updateMembershipPlan) are untouched
// by that change and are not covered here.
@ExtendWith(MockitoExtension.class)
class AdminMembershipServiceTest {

    @Mock UserRepository         userRepository;
    @Mock MembershipRepository   membershipRepository;
    @Mock PaymentRepository      paymentRepository;
    @Mock PlanRepository         planRepository;
    @Mock SeatRepository         seatRepository;
    @Mock SeatBookingRepository  seatBookingRepository;
    @Mock AppSettingsRepository  appSettingsRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock RedisTemplate<String, Object> redisTemplate;

    @InjectMocks AdminMembershipService adminMembershipService;

    private Membership buildMembership(UUID id, Membership.Status status) {
        return Membership.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .seatNumber("C13")
                .shift("FULL_DAY")
                .startDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.now().minusDays(1))
                .status(status)
                .duesAmount(status == Membership.Status.GRACE ? new BigDecimal("500.00") : null)
                .build();
    }

    private SeatBooking buildActiveBooking(UUID membershipId) {
        return SeatBooking.builder()
                .id(UUID.randomUUID())
                .seatId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .membershipId(membershipId)
                .shift("FULL_DAY")
                .bookingDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.of(9999, 12, 31))
                .status(SeatBooking.Status.ACTIVE)
                .build();
    }

    // ── releaseSeat ──────────────────────────────────────────────────────────

    @Test
    void releaseSeat_graceMembership_transitionsToExpiredAndReleasesSeat() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.GRACE);
        SeatBooking booking = buildActiveBooking(id);

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(id, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.of(booking));

        adminMembershipService.releaseSeat(id.toString());

        assertThat(mem.getStatus()).isEqualTo(Membership.Status.EXPIRED);
        assertThat(booking.getStatus()).isEqualTo(SeatBooking.Status.RELEASED);
        verify(membershipRepository).save(mem);
        verify(seatBookingRepository).save(booking);
    }

    @Test
    void releaseSeat_activeMembership_transitionsToExpiredAndReleasesSeat() {
        // The behavior this change adds: releasing a seat that's still ACTIVE
        // (paid, on-schedule) and not just one that lapsed into GRACE.
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        SeatBooking booking = buildActiveBooking(id);

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(id, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.of(booking));

        adminMembershipService.releaseSeat(id.toString());

        assertThat(mem.getStatus()).isEqualTo(Membership.Status.EXPIRED);
        assertThat(booking.getStatus()).isEqualTo(SeatBooking.Status.RELEASED);
        verify(membershipRepository).save(mem);
        verify(seatBookingRepository).save(booking);
    }

    @Test
    void releaseSeat_pendingMembership_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.PENDING);
        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));

        assertThatThrownBy(() -> adminMembershipService.releaseSeat(id.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no currently-occupied seat");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(seatBookingRepository);
    }

    @Test
    void releaseSeat_alreadyExpiredMembership_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.EXPIRED);
        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));

        assertThatThrownBy(() -> adminMembershipService.releaseSeat(id.toString()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void releaseSeat_membershipNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(membershipRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminMembershipService.releaseSeat(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void releaseSeat_noActiveSeatBooking_stillExpiresMembershipWithoutError() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.GRACE);
        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(id, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.empty());

        adminMembershipService.releaseSeat(id.toString());

        assertThat(mem.getStatus()).isEqualTo(Membership.Status.EXPIRED);
        verify(membershipRepository).save(mem);
        verify(seatBookingRepository, never()).save(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void releaseSeat_fullDayShift_invalidatesFullDayMorningAndEveningCache() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        SeatBooking booking = buildActiveBooking(id); // shift = FULL_DAY

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(id, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.of(booking));

        adminMembershipService.releaseSeat(id.toString());

        // invalidateSeatCache walks LocalDate.now()..+14 inclusive (15 days) and,
        // for FULL_DAY, deletes 3 keys per day (FULL_DAY + MORNING + EVENING).
        verify(redisTemplate, times(15 * 3)).delete(anyString());
    }
}
