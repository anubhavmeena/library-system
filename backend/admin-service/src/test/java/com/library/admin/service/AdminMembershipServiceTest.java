package com.library.admin.service;

import com.library.admin.dto.ChangeSeatRequest;
import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.MarkPendingRequest;
import com.library.admin.event.BookingConfirmedEvent;
import com.library.admin.entity.Membership;
import com.library.admin.entity.Payment;
import com.library.admin.entity.Plan;
import com.library.admin.entity.Seat;
import com.library.admin.entity.SeatBooking;
import com.library.admin.entity.User;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Covers AdminMembershipService.releaseSeat() (changed to allow releasing an
// ACTIVE, not just GRACE, membership's seat), changeSeat() (changed to create
// a SeatBooking when the membership never had one, instead of silently
// no-oping), and createCashMembership()'s new paid+pending == plan price
// validation. updateMembershipPlan is untouched by any of these changes and
// is not covered here.
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

    private Seat buildSeat(UUID id, String seatNumber) {
        return new Seat(id, seatNumber, seatNumber.substring(0, 1),
                Integer.parseInt(seatNumber.substring(1)), true);
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

    // ── changeSeat ───────────────────────────────────────────────────────────

    @Test
    void changeSeat_existingBooking_updatesSeatIdInPlace() {
        UUID membershipId = UUID.randomUUID();
        Membership mem = buildMembership(membershipId, Membership.Status.ACTIVE);
        SeatBooking existingBooking = buildActiveBooking(membershipId);
        UUID newSeatId = UUID.randomUUID();
        Seat newSeat = buildSeat(newSeatId, "D5");
        ChangeSeatRequest req = new ChangeSeatRequest();
        req.setSeatNumber("D5");

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(mem));
        when(seatRepository.findBySeatNumber("D5")).thenReturn(Optional.of(newSeat));
        when(seatBookingRepository.findActiveBookingsForSeat(eq(newSeatId), any(), any()))
                .thenReturn(List.of());
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(membershipId, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.of(existingBooking));

        adminMembershipService.changeSeat(membershipId.toString(), req);

        assertThat(existingBooking.getSeatId()).isEqualTo(newSeatId);
        verify(seatBookingRepository).save(existingBooking);
        verify(seatBookingRepository, times(1)).save(any());
    }

    @Test
    void changeSeat_noExistingBooking_createsNewSeatBooking() {
        // Regression for a production bug: a membership can reach changeSeat()
        // having never had a SeatBooking row at all (e.g. the student booking
        // flow activated the membership but the separate seat-reservation call
        // never completed). Silently doing nothing here — the old behavior —
        // left the membership pointing at a seat with no booking record behind
        // it. It must create one instead.
        UUID membershipId = UUID.randomUUID();
        Membership mem = buildMembership(membershipId, Membership.Status.ACTIVE);
        UUID newSeatId = UUID.randomUUID();
        Seat newSeat = buildSeat(newSeatId, "D5");
        ChangeSeatRequest req = new ChangeSeatRequest();
        req.setSeatNumber("D5");

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(mem));
        when(seatRepository.findBySeatNumber("D5")).thenReturn(Optional.of(newSeat));
        when(seatBookingRepository.findActiveBookingsForSeat(eq(newSeatId), any(), any()))
                .thenReturn(List.of());
        when(seatBookingRepository.findFirstByMembershipIdAndStatus(membershipId, SeatBooking.Status.ACTIVE))
                .thenReturn(Optional.empty());

        adminMembershipService.changeSeat(membershipId.toString(), req);

        ArgumentCaptor<SeatBooking> captor = ArgumentCaptor.forClass(SeatBooking.class);
        verify(seatBookingRepository).save(captor.capture());
        SeatBooking created = captor.getValue();
        assertThat(created.getSeatId()).isEqualTo(newSeatId);
        assertThat(created.getUserId()).isEqualTo(mem.getUserId());
        assertThat(created.getMembershipId()).isEqualTo(membershipId);
        assertThat(created.getShift()).isEqualTo(mem.getShift());
        assertThat(created.getBookingDate()).isEqualTo(mem.getStartDate());
        assertThat(created.getEndDate()).isEqualTo(mem.getEndDate());
        assertThat(created.getStatus()).isEqualTo(SeatBooking.Status.ACTIVE);
    }

    @Test
    void changeSeat_notActiveMembership_throwsIllegalArgumentException() {
        UUID membershipId = UUID.randomUUID();
        Membership mem = buildMembership(membershipId, Membership.Status.GRACE);
        ChangeSeatRequest req = new ChangeSeatRequest();
        req.setSeatNumber("D5");

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(mem));

        assertThatThrownBy(() -> adminMembershipService.changeSeat(membershipId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");

        verifyNoInteractions(seatBookingRepository);
    }

    // ── createCashMembership ─────────────────────────────────────────────────

    @Test
    void createCashMembership_paidPlusPendingMismatchesPlanPrice_throwsAndSavesNothingFinancial() {
        // Regression for a real production bug: the admin "New Membership" form
        // could submit paidAmount=600 and pendingAmount=600 for a ₹600 plan
        // (a stale, unsynced field) — double-counting the same amount as both
        // paid and still owed. The validation must catch this before the
        // Payment/SeatBooking rows are ever written.
        UUID studentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        User student = User.builder().id(studentId).name("Sunil Meena").mobile("9990000000")
                .role(User.Role.STUDENT).build();
        Plan plan = Plan.builder().id(planId).name("Full Day").planType(Plan.PlanType.FULL_DAY)
                .price(new BigDecimal("600.00")).durationDays(30).isActive(true).build();
        Seat seat = new Seat(UUID.randomUUID(), "B14", "B", 14, true);

        CreateCashMembershipRequest req = new CreateCashMembershipRequest();
        req.setStudentId(studentId.toString());
        req.setPlanId(planId.toString());
        req.setSeatNumber("B14");
        req.setPaidAmount(new BigDecimal("600.00"));
        req.setPendingAmount(new BigDecimal("600.00"));

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(studentId, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(studentId))
                .thenReturn(Optional.empty());
        when(seatRepository.findBySeatNumber("B14")).thenReturn(Optional.of(seat));
        when(seatBookingRepository.findActiveBookingsForSeat(eq(seat.getId()), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> adminMembershipService.createCashMembership(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal the plan price");

        verify(paymentRepository, never()).save(any());
        verify(seatBookingRepository, never()).save(any());
    }

    @Test
    void createCashMembership_paidPlusPendingMatchesPlanPrice_doesNotThrow() {
        UUID studentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        User student = User.builder().id(studentId).name("Sunil Meena").mobile("9990000000")
                .role(User.Role.STUDENT).build();
        Plan plan = Plan.builder().id(planId).name("Full Day").planType(Plan.PlanType.FULL_DAY)
                .price(new BigDecimal("600.00")).durationDays(30).isActive(true).build();
        Seat seat = new Seat(UUID.randomUUID(), "B14", "B", 14, true);

        CreateCashMembershipRequest req = new CreateCashMembershipRequest();
        req.setStudentId(studentId.toString());
        req.setPlanId(planId.toString());
        req.setSeatNumber("B14");
        req.setPaidAmount(BigDecimal.ZERO);
        req.setPendingAmount(new BigDecimal("600.00"));

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(studentId, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(studentId))
                .thenReturn(Optional.empty());
        when(seatRepository.findBySeatNumber("B14")).thenReturn(Optional.of(seat));
        when(seatBookingRepository.findActiveBookingsForSeat(eq(seat.getId()), any(), any()))
                .thenReturn(List.of());
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            Membership m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        assertThatCode(() -> adminMembershipService.createCashMembership(req))
                .doesNotThrowAnyException();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getPendingAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void createCashMembership_publishesBookingConfirmedEventWithPhotoUrl() {
        UUID studentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        User student = User.builder().id(studentId).name("Sunil Meena").mobile("9990000000")
                .photoUrl("/uploads/photos/user_sunil.jpg").role(User.Role.STUDENT).build();
        Plan plan = Plan.builder().id(planId).name("Full Day").planType(Plan.PlanType.FULL_DAY)
                .price(new BigDecimal("600.00")).durationDays(30).isActive(true).build();
        Seat seat = new Seat(UUID.randomUUID(), "B14", "B", 14, true);

        CreateCashMembershipRequest req = new CreateCashMembershipRequest();
        req.setStudentId(studentId.toString());
        req.setPlanId(planId.toString());
        req.setSeatNumber("B14");
        req.setPaidAmount(new BigDecimal("600.00"));
        req.setPendingAmount(BigDecimal.ZERO);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(studentId, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(studentId))
                .thenReturn(Optional.empty());
        when(seatRepository.findBySeatNumber("B14")).thenReturn(Optional.of(seat));
        when(seatBookingRepository.findActiveBookingsForSeat(eq(seat.getId()), any(), any()))
                .thenReturn(List.of());
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            Membership m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        adminMembershipService.createCashMembership(req);

        ArgumentCaptor<BookingConfirmedEvent> cap = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(kafkaTemplate).send(eq("booking-confirmed"), eq(studentId.toString()), cap.capture());
        assertThat(cap.getValue().getPhotoUrl()).isEqualTo("/uploads/photos/user_sunil.jpg");
    }
    // ── markMembershipPending / markMembershipGrace ─────────────────────────────
    // Admin correction for a membership wrongly marked fully paid — deletes the
    // erroneous Payment row and either replaces it with a corrected one (Pending)
    // or moves the membership to GRACE with dues = full plan price (no payment
    // row needed for GRACE, since GRACE dues live on Membership.duesAmount).

    @Test
    void markMembershipPending_activeMembership_deletesOldPaymentAndCreatesCorrectedOne() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        Plan plan = Plan.builder().id(mem.getPlanId()).name("Full Day")
                .planType(Plan.PlanType.FULL_DAY).price(new BigDecimal("600.00")).build();
        Payment oldPayment = Payment.builder().id(UUID.randomUUID()).membershipId(id)
                .userId(mem.getUserId()).amount(new BigDecimal("600.00"))
                .pendingAmount(BigDecimal.ZERO).status(Payment.Status.SUCCESS).build();

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(plan));
        when(paymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc(id))
                .thenReturn(Optional.of(oldPayment));

        MarkPendingRequest req = new MarkPendingRequest();
        req.setPendingAmount(new BigDecimal("200.00"));

        adminMembershipService.markMembershipPending(id.toString(), req);

        verify(paymentRepository).delete(oldPayment);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("400.00");
        assertThat(captor.getValue().getPendingAmount()).isEqualByComparingTo("200.00");
        assertThat(captor.getValue().getMembershipId()).isEqualTo(id);
        assertThat(mem.getStatus()).isEqualTo(Membership.Status.ACTIVE);
    }

    @Test
    void markMembershipPending_noExistingPayment_stillCreatesNewOne() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        Plan plan = Plan.builder().id(mem.getPlanId()).name("Full Day")
                .planType(Plan.PlanType.FULL_DAY).price(new BigDecimal("600.00")).build();

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(plan));
        when(paymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc(id))
                .thenReturn(Optional.empty());

        MarkPendingRequest req = new MarkPendingRequest();
        req.setPendingAmount(new BigDecimal("600.00"));

        assertThatCode(() -> adminMembershipService.markMembershipPending(id.toString(), req))
                .doesNotThrowAnyException();

        verify(paymentRepository, never()).delete(any());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0.00");
        assertThat(captor.getValue().getPendingAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void markMembershipPending_nonActiveMembership_throws() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.GRACE);
        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));

        MarkPendingRequest req = new MarkPendingRequest();
        req.setPendingAmount(new BigDecimal("200.00"));

        assertThatThrownBy(() -> adminMembershipService.markMembershipPending(id.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void markMembershipPending_amountExceedsPlanPrice_throws() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        Plan plan = Plan.builder().id(mem.getPlanId()).name("Full Day")
                .planType(Plan.PlanType.FULL_DAY).price(new BigDecimal("600.00")).build();

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(plan));

        MarkPendingRequest req = new MarkPendingRequest();
        req.setPendingAmount(new BigDecimal("700.00"));

        assertThatThrownBy(() -> adminMembershipService.markMembershipPending(id.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed the plan price");

        verify(paymentRepository, never()).save(any());
        verify(paymentRepository, never()).delete(any());
    }

    @Test
    void markMembershipGrace_activeMembership_setsGraceDuesToPlanPriceAndDeletesPayment() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.ACTIVE);
        Plan plan = Plan.builder().id(mem.getPlanId()).name("Full Day")
                .planType(Plan.PlanType.FULL_DAY).price(new BigDecimal("600.00")).build();
        Payment oldPayment = Payment.builder().id(UUID.randomUUID()).membershipId(id)
                .userId(mem.getUserId()).amount(new BigDecimal("600.00"))
                .pendingAmount(BigDecimal.ZERO).status(Payment.Status.SUCCESS).build();

        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));
        when(planRepository.findById(mem.getPlanId())).thenReturn(Optional.of(plan));
        when(paymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc(id))
                .thenReturn(Optional.of(oldPayment));

        adminMembershipService.markMembershipGrace(id.toString());

        verify(paymentRepository).delete(oldPayment);
        verify(paymentRepository, never()).save(any());
        assertThat(mem.getStatus()).isEqualTo(Membership.Status.GRACE);
        assertThat(mem.getDuesAmount()).isEqualByComparingTo("600.00");
        verify(membershipRepository).save(mem);
    }

    @Test
    void markMembershipGrace_nonActiveMembership_throws() {
        UUID id = UUID.randomUUID();
        Membership mem = buildMembership(id, Membership.Status.GRACE);
        when(membershipRepository.findById(id)).thenReturn(Optional.of(mem));

        assertThatThrownBy(() -> adminMembershipService.markMembershipGrace(id.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");

        verifyNoInteractions(paymentRepository);
    }
}
