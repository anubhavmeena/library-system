package com.library.admin.service;

import com.library.admin.dto.ChangeSeatRequest;
import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.MembershipDto;
import com.library.admin.entity.*;
import com.library.admin.event.BookingConfirmedEvent;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMembershipService {

    private final UserRepository         userRepository;
    private final MembershipRepository   membershipRepository;
    private final PaymentRepository      paymentRepository;
    private final PlanRepository         planRepository;
    private final SeatRepository         seatRepository;
    private final SeatBookingRepository  seatBookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public MembershipDto createCashMembership(CreateCashMembershipRequest req) {

        // 1. Load student
        User student = userRepository.findById(UUID.fromString(req.getStudentId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // 2. Load plan
        Plan plan = planRepository.findById(UUID.fromString(req.getPlanId()))
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        // 3. Validate shift
        String shift = req.getShift();
        if (plan.getPlanType() == Plan.PlanType.FULL_DAY) {
            shift = "FULL_DAY";
        } else {
            if (shift == null || (!shift.equals("MORNING") && !shift.equals("EVENING"))) {
                throw new IllegalArgumentException("MORNING or EVENING shift required for HALF_DAY plans");
            }
        }

        // 4. Check student has no active membership, and no unresolved GRACE
        // membership (dues owed / seat held) that would need clearing or an
        // explicit admin release first.
        Optional<Membership> existing = membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(
                student.getId(), Membership.Status.ACTIVE);
        if (existing.isPresent() && !existing.get().getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Student already has an active membership");
        }
        membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(student.getId())
                .filter(m -> m.getStatus() == Membership.Status.GRACE)
                .ifPresent(g -> {
                    throw new IllegalArgumentException(
                            "Student has an overdue membership with pending dues — clear the dues " +
                            "or release the seat before creating a new membership");
                });

        // 5. Parse start/end dates
        LocalDate startDate = (req.getStartDate() != null && !req.getStartDate().isBlank())
                ? LocalDate.parse(req.getStartDate())
                : LocalDate.now();
        LocalDate endDate = startDate.plusDays(plan.getDurationDays());

        // 6. Look up seat
        Seat seat = seatRepository.findBySeatNumber(req.getSeatNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + req.getSeatNumber()));
        if (!Boolean.TRUE.equals(seat.getIsActive())) {
            throw new IllegalArgumentException("Seat " + req.getSeatNumber() + " is not active");
        }

        // 7. Check seat conflicts: same seat, overlapping dates, same shift (or FULL_DAY)
        final String resolvedShift = shift;
        List<SeatBooking> existingBookings = seatBookingRepository
                .findActiveBookingsForSeat(seat.getId(), startDate, endDate);
        boolean hasConflict = existingBookings.stream().anyMatch(b ->
                b.getShift().equals("FULL_DAY") ||
                resolvedShift.equals("FULL_DAY") ||
                b.getShift().equals(resolvedShift));
        if (hasConflict) {
            throw new IllegalArgumentException(
                    "Seat " + req.getSeatNumber() + " is already booked for the selected shift and dates");
        }

        // 8. Save Membership (immediately ACTIVE)
        Membership membership = Membership.builder()
                .userId(student.getId())
                .planId(plan.getId())
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .shift(resolvedShift)
                .startDate(startDate)
                .endDate(endDate)
                .status(Membership.Status.ACTIVE)
                .build();
        membership = membershipRepository.save(membership);

        // 9. Save Payment (SUCCESS, CASH)
        BigDecimal planPrice     = plan.getPrice();
        BigDecimal paidAmount    = (req.getPaidAmount()    != null) ? req.getPaidAmount()    : planPrice;
        BigDecimal pendingAmount = (req.getPendingAmount() != null) ? req.getPendingAmount() : BigDecimal.ZERO;

        String cashOrderId = "cash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(student.getId())
                .amount(paidAmount)
                .pendingAmount(pendingAmount)
                .paymentGateway("CASH")
                .gatewayOrderId(cashOrderId)
                .status(Payment.Status.SUCCESS)
                .build();
        paymentRepository.save(payment);

        // 10. Save SeatBooking
        SeatBooking booking = SeatBooking.builder()
                .seatId(seat.getId())
                .userId(student.getId())
                .membershipId(membership.getId())
                .shift(resolvedShift)
                .bookingDate(startDate)
                .endDate(endDate)
                .status(SeatBooking.Status.ACTIVE)
                .build();
        seatBookingRepository.save(booking);

        // Bust seat-service's Redis cache so /seats/availability reflects this
        // booking immediately — this write bypasses seat-service's own bookSeat(),
        // which is the only other place that busts this cache.
        invalidateSeatCache(resolvedShift, startDate, endDate);

        // 11. Publish booking-confirmed Kafka event (best-effort)
        try {
            BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                    .userId(student.getId().toString())
                    .membershipId(membership.getId().toString())
                    .userName(student.getName())
                    .userMobile(student.getMobile())
                    .userEmail(student.getEmail())
                    .planName(plan.getName())
                    .planType(plan.getPlanType().name())
                    .seatNumber(seat.getSeatNumber())
                    .shift(resolvedShift)
                    .startDate(startDate.toString())
                    .endDate(endDate.toString())
                    .amountPaid(plan.getPrice())
                    .build();
            kafkaTemplate.send("booking-confirmed", student.getId().toString(), event);
        } catch (Exception e) {
            log.warn("Failed to publish booking-confirmed event for membership {}: {}",
                    membership.getId(), e.getMessage());
        }

        return MembershipDto.fromEntity(membership, plan.getName());
    }

    @Transactional
    public MembershipDto changeSeat(String membershipId, ChangeSeatRequest req) {

        // 1. Load membership
        Membership membership = membershipRepository.findById(UUID.fromString(membershipId))
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found: " + membershipId));
        if (membership.getStatus() != Membership.Status.ACTIVE) {
            throw new IllegalArgumentException("Seat can only be changed for an ACTIVE membership");
        }

        // 2. Load new seat
        Seat newSeat = seatRepository.findBySeatNumber(req.getSeatNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + req.getSeatNumber()));
        if (!Boolean.TRUE.equals(newSeat.getIsActive())) {
            throw new IllegalArgumentException("Seat " + req.getSeatNumber() + " is not active");
        }

        // 3. Conflict check — exclude this membership's own booking
        List<SeatBooking> conflicts = seatBookingRepository
                .findActiveBookingsForSeat(newSeat.getId(), membership.getStartDate(), membership.getEndDate());
        String shift = membership.getShift();
        boolean hasConflict = conflicts.stream()
                .filter(b -> !b.getMembershipId().equals(membership.getId()))
                .anyMatch(b -> b.getShift().equals("FULL_DAY") ||
                               shift.equals("FULL_DAY") ||
                               b.getShift().equals(shift));
        if (hasConflict) {
            throw new IllegalArgumentException(
                    "Seat " + req.getSeatNumber() + " is already booked for the selected shift and dates");
        }

        // 4. Update Membership
        membership.setSeatId(newSeat.getId());
        membership.setSeatNumber(newSeat.getSeatNumber());
        membershipRepository.save(membership);

        // 5. Update SeatBooking
        seatBookingRepository
                .findFirstByMembershipIdAndStatus(membership.getId(), SeatBooking.Status.ACTIVE)
                .ifPresent(b -> {
                    b.setSeatId(newSeat.getId());
                    seatBookingRepository.save(b);
                });

        // Bust seat-service's Redis cache — both the old and new seat's occupancy
        // for this shift/date range changed
        invalidateSeatCache(membership.getShift(), membership.getStartDate(), membership.getEndDate());

        log.info("Seat changed for membership {} from {} to {}",
                membershipId, membership.getSeatNumber(), req.getSeatNumber());

        // Resolve plan name for DTO
        String planName = planRepository.findById(membership.getPlanId())
                .map(Plan::getName).orElse(null);
        return MembershipDto.fromEntity(membership, planName);
    }

    @Transactional
    public void updateMembershipPlan(String membershipId, com.library.admin.dto.UpdateMembershipPlanRequest req) {
        Membership membership = membershipRepository.findById(UUID.fromString(membershipId))
                .orElseThrow(() -> new com.library.admin.exception.ResourceNotFoundException(
                        "Membership not found: " + membershipId));
        Plan plan = planRepository.findById(UUID.fromString(req.getPlanId()))
                .orElseThrow(() -> new com.library.admin.exception.ResourceNotFoundException(
                        "Plan not found: " + req.getPlanId()));
        if (!Boolean.TRUE.equals(plan.getIsActive()))
            throw new IllegalArgumentException("Plan is not active");

        membership.setPlanId(plan.getId());
        membership.setEndDate(membership.getStartDate().plusDays(plan.getDurationDays()));
        membershipRepository.save(membership);
        log.info("Plan updated for membership {} to plan {}", membershipId, plan.getName());
    }

    // Admin-only escape hatch: explicitly frees a seat that's been held in GRACE
    // because the student never paid their dues. Finalizes the membership as
    // EXPIRED (dues remain on record — not auto-waived) and releases the seat
    // booking so it becomes bookable by other students again.
    @Transactional
    public void releaseSeat(String membershipId) {
        Membership mem = membershipRepository.findById(UUID.fromString(membershipId))
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found: " + membershipId));

        if (mem.getStatus() != Membership.Status.GRACE) {
            throw new IllegalArgumentException("Only a membership in GRACE can be released");
        }

        mem.setStatus(Membership.Status.EXPIRED);
        membershipRepository.save(mem);

        seatBookingRepository.findFirstByMembershipIdAndStatus(mem.getId(), SeatBooking.Status.ACTIVE)
                .ifPresent(booking -> {
                    booking.setStatus(SeatBooking.Status.RELEASED);
                    seatBookingRepository.save(booking);
                    // Bounded — booking.endDate is the far-future hold sentinel, never use it as a loop bound.
                    invalidateSeatCache(booking.getShift(), LocalDate.now(), LocalDate.now().plusDays(14));
                });

        log.info("Seat {} released by admin for membership {} (dues {} remain on record)",
                mem.getSeatNumber(), membershipId, mem.getDuesAmount());
    }

    // ── Seat Cache ────────────────────────────────────────────────────────────
    // Mirrors seat-service's SeatService.invalidateCache() exactly. Needed here
    // because admin-created bookings write seat_bookings directly instead of
    // calling seat-service's HTTP API (see backend/CLAUDE.md: admin-service
    // queries the DB directly rather than calling sibling services), so this
    // is the only place that can bust seat-service's Redis cache for those writes.
    //
    // Public (not private) so ExpiryReminderScheduler (different package) can
    // reuse it too. Callers MUST pass a bounded range — this loops day-by-day
    // between start and end, so it must never be called with SeatBooking's
    // far-future hold sentinel as the end bound.
    public void invalidateSeatCache(String shift, LocalDate start, LocalDate end) {
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            redisTemplate.delete("seats:availability:" + shift + ":" + cursor);
            if ("FULL_DAY".equals(shift)) {
                redisTemplate.delete("seats:availability:MORNING:" + cursor);
                redisTemplate.delete("seats:availability:EVENING:" + cursor);
            }
            cursor = cursor.plusDays(1);
        }
    }
}
