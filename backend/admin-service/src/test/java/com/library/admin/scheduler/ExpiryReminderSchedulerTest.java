package com.library.admin.scheduler;

import com.library.admin.dto.RenewalReminderEvent;
import com.library.admin.entity.Membership;
import com.library.admin.entity.Plan;
import com.library.admin.entity.SeatBooking;
import com.library.admin.entity.User;
import com.library.admin.repository.MembershipRepository;
import com.library.admin.repository.PlanRepository;
import com.library.admin.repository.SeatBookingRepository;
import com.library.admin.repository.UserRepository;
import com.library.admin.service.AdminMembershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryReminderSchedulerTest {

    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository       userRepository;
    @Mock PlanRepository       planRepository;
    @Mock SeatBookingRepository seatBookingRepository;
    @Mock AdminMembershipService adminMembershipService;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks ExpiryReminderScheduler scheduler;

    private Plan plan(UUID id, BigDecimal price) {
        return Plan.builder().id(id).name("Full Day").price(price).durationDays(30).build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Membership membership(UUID userId, int daysFromNow) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planId(UUID.randomUUID())
                .seatNumber("B7")
                .shift("MORNING")
                .startDate(LocalDate.now().minusDays(20))
                .endDate(LocalDate.now().plusDays(daysFromNow))
                .status(Membership.Status.ACTIVE)
                .reminderSent(false)
                .build();
    }

    private User user(UUID id) {
        return User.builder()
                .id(id)
                .name("Bob")
                .mobile("9000000001")
                .email("bob@test.com")
                .role(User.Role.STUDENT)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── Empty candidate list ─────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_noExpiringMemberships_returnsEarly() {
        when(membershipRepository.findExpiringMemberships(any(), any()))
                .thenReturn(List.of());

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(membershipRepository, never()).save(any());
    }

    // ── 7-day threshold fires ────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_daysLeft7_sendsKafkaEventAndSetsFlag() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 7);
        User u = user(uid);

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate).send(eq("renewal-reminder"), eq(uid.toString()), any(RenewalReminderEvent.class));
        verify(membershipRepository).save(argThat(m -> m.isReminderSent()));
    }

    // ── 3-day threshold fires ────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_daysLeft3_sendsKafkaEventAndSetsFlag() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 3);
        User u = user(uid);

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate).send(eq("renewal-reminder"), eq(uid.toString()), any(RenewalReminderEvent.class));
        verify(membershipRepository).save(argThat(m -> m.isReminderSent()));
    }

    // ── Non-threshold days are skipped ───────────────────────────────────────

    @ParameterizedTest(name = "daysLeft={0} should NOT fire Kafka")
    @ValueSource(ints = {6, 5, 4, 2, 1, 0})
    void sendExpiryReminders_nonThresholdDays_skipped(int daysLeft) {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, daysLeft);
        User u = user(uid);

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(membershipRepository, never()).save(any()); // reminderSent NOT set
    }

    // ── User not found ───────────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_userNotInMap_skipped() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 7);

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of()); // user missing

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(membershipRepository, never()).save(any());
    }

    // ── Mixed batch ──────────────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_mixedDays_onlyThresholdDaysFire() {
        UUID uid7 = UUID.randomUUID();
        UUID uid3 = UUID.randomUUID();
        UUID uid5 = UUID.randomUUID();

        Membership m7 = membership(uid7, 7);
        Membership m3 = membership(uid3, 3);
        Membership m5 = membership(uid5, 5); // skipped

        when(membershipRepository.findExpiringMemberships(any(), any()))
                .thenReturn(List.of(m7, m3, m5));
        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(user(uid7), user(uid3), user(uid5)));

        scheduler.sendExpiryReminders();

        verify(kafkaTemplate, times(2)).send(eq("renewal-reminder"), anyString(), any(RenewalReminderEvent.class));
        verify(membershipRepository, times(2)).save(any());
    }

    // ── Kafka event payload ──────────────────────────────────────────────────

    @Test
    void sendExpiryReminders_day7_kafkaEventFieldsAreCorrect() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 7);
        User u = user(uid);

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));

        scheduler.sendExpiryReminders();

        ArgumentCaptor<RenewalReminderEvent> captor = ArgumentCaptor.forClass(RenewalReminderEvent.class);
        verify(kafkaTemplate).send(eq("renewal-reminder"), eq(uid.toString()), captor.capture());

        RenewalReminderEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(uid.toString());
        assertThat(event.getMembershipId()).isEqualTo(mem.getId().toString());
        assertThat(event.getUserName()).isEqualTo("Bob");
        assertThat(event.getUserMobile()).isEqualTo("9000000001");
        assertThat(event.getUserEmail()).isEqualTo("bob@test.com");
        assertThat(event.getSeatNumber()).isEqualTo("B7");
        assertThat(event.getExpiryDate()).isEqualTo(mem.getEndDate().toString());
        assertThat(event.getDaysRemaining()).isEqualTo(7);
        assertThat(event.getEventType()).isEqualTo("RENEWAL_REMINDER");
    }

    // ── Repo called with correct date range ──────────────────────────────────

    @Test
    void sendExpiryReminders_queriesCorrectDateRange() {
        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of());

        scheduler.sendExpiryReminders();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor   = ArgumentCaptor.forClass(LocalDate.class);
        verify(membershipRepository).findExpiringMemberships(fromCaptor.capture(), toCaptor.capture());

        LocalDate today = LocalDate.now();
        assertThat(fromCaptor.getValue()).isEqualTo(today);
        assertThat(toCaptor.getValue()).isEqualTo(today.plusDays(7));
    }

    // ── ReminderSent flag set correctly ──────────────────────────────────────

    @Test
    void sendExpiryReminders_reminderSentFlagSetToTrueAfterSend() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 7);
        assertThat(mem.isReminderSent()).isFalse(); // pre-condition

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user(uid)));

        scheduler.sendExpiryReminders();

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(saved.capture());
        assertThat(saved.getValue().isReminderSent()).isTrue();
    }

    @Test
    void sendExpiryReminders_skippedMembership_reminderSentStaysFalse() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, 5); // non-threshold day

        when(membershipRepository.findExpiringMemberships(any(), any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user(uid)));

        scheduler.sendExpiryReminders();

        assertThat(mem.isReminderSent()).isFalse(); // not modified
        verify(membershipRepository, never()).save(any());
    }

    // ── markExpiredAndStartGrace ───────────────────────────────────────────────

    @Test
    void markExpiredAndStartGrace_noExpiredMemberships_returnsEarly() {
        when(membershipRepository.findExpiredActive(any())).thenReturn(List.of());

        scheduler.markExpiredAndStartGrace();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void markExpiredAndStartGrace_noQueuedRenewal_entersGraceWithDues() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, -1); // expired yesterday
        User u = user(uid);
        Plan p = plan(mem.getPlanId(), new BigDecimal("999"));

        when(membershipRepository.findExpiredActive(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));
        when(membershipRepository.findQueuedByUserId(uid)).thenReturn(Optional.empty());
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(p));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(mem.getId(), SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.empty());

        scheduler.markExpiredAndStartGrace();

        ArgumentCaptor<Membership> memCaptor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository, atLeastOnce()).save(memCaptor.capture());
        assertThat(memCaptor.getAllValues()).anyMatch(m ->
                m.getStatus() == Membership.Status.GRACE && new BigDecimal("999").compareTo(m.getDuesAmount()) == 0);
        verify(kafkaTemplate, times(2)).send(eq("renewal-reminder"), eq(uid.toString()), any(RenewalReminderEvent.class));
    }

    @Test
    void markExpiredAndStartGrace_kafkaEvents_haveGraceTypesAndDues() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, -1);
        User u = user(uid);
        Plan p = plan(mem.getPlanId(), new BigDecimal("999"));

        when(membershipRepository.findExpiredActive(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));
        when(membershipRepository.findQueuedByUserId(uid)).thenReturn(Optional.empty());
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(p));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(mem.getId(), SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.empty());

        scheduler.markExpiredAndStartGrace();

        ArgumentCaptor<RenewalReminderEvent> captor = ArgumentCaptor.forClass(RenewalReminderEvent.class);
        verify(kafkaTemplate, times(2)).send(eq("renewal-reminder"), eq(uid.toString()), captor.capture());
        List<RenewalReminderEvent> events = captor.getAllValues();

        assertThat(events).anyMatch(e -> "MEMBERSHIP_GRACE_STARTED".equals(e.getEventType()));
        assertThat(events).anyMatch(e -> "MEMBERSHIP_EXPIRED_GRACE".equals(e.getEventType()));
        assertThat(events).allMatch(e -> e.getDaysRemaining() == 0);
        assertThat(events).allMatch(e -> "B7".equals(e.getSeatNumber()));
        assertThat(events).allMatch(e -> "Bob".equals(e.getUserName()));
        assertThat(events).allMatch(e -> new BigDecimal("999").compareTo(e.getPendingAmount()) == 0);
    }

    @Test
    void markExpiredAndStartGrace_seatBookingHeld_endDatePushedToSentinelAndCacheBusted() {
        UUID uid = UUID.randomUUID();
        Membership mem = membership(uid, -1);
        User u = user(uid);
        Plan p = plan(mem.getPlanId(), new BigDecimal("999"));
        SeatBooking booking = SeatBooking.builder()
                .id(UUID.randomUUID()).seatId(UUID.randomUUID()).userId(uid).membershipId(mem.getId())
                .shift("MORNING").bookingDate(mem.getStartDate()).endDate(mem.getEndDate())
                .status(SeatBooking.Status.ACTIVE).build();

        when(membershipRepository.findExpiredActive(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));
        when(membershipRepository.findQueuedByUserId(uid)).thenReturn(Optional.empty());
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(p));
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(mem.getId(), SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.of(booking));

        scheduler.markExpiredAndStartGrace();

        ArgumentCaptor<SeatBooking> bookingCaptor = ArgumentCaptor.forClass(SeatBooking.class);
        verify(seatBookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getEndDate()).isEqualTo(LocalDate.of(9999, 12, 31));

        // Cache invalidation must be bounded — never span out to the sentinel date.
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor   = ArgumentCaptor.forClass(LocalDate.class);
        verify(adminMembershipService).invalidateSeatCache(eq("MORNING"), startCaptor.capture(), endCaptor.capture());
        assertThat(endCaptor.getValue()).isBefore(LocalDate.now().plusDays(30));
    }

    @Test
    void markExpiredAndStartGrace_queuedPlanExists_getsActivatedWithoutGrace() {
        UUID uid = UUID.randomUUID();
        Membership expired = membership(uid, -1);
        Membership queued  = membership(uid, 30);
        queued.setStatus(Membership.Status.QUEUED);
        User u = user(uid);

        when(membershipRepository.findExpiredActive(any())).thenReturn(List.of(expired));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u));
        when(membershipRepository.findQueuedByUserId(uid)).thenReturn(Optional.of(queued));

        scheduler.markExpiredAndStartGrace();

        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository, times(2)).save(captor.capture());
        List<Membership> saved = captor.getAllValues();
        assertThat(saved).anyMatch(m -> m.getStatus() == Membership.Status.EXPIRED);
        assertThat(saved).anyMatch(m -> m.getStatus() == Membership.Status.ACTIVE);
        // Happy path — no grace/dues machinery should be touched at all.
        verifyNoInteractions(planRepository, seatBookingRepository, adminMembershipService);
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
