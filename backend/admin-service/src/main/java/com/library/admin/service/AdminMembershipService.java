package com.library.admin.service;

import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.MembershipDto;
import com.library.admin.entity.*;
import com.library.admin.event.BookingConfirmedEvent;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // 4. Check student has no active membership
        Optional<Membership> existing = membershipRepository.findByUserIdAndStatus(
                student.getId(), Membership.Status.ACTIVE);
        if (existing.isPresent() && !existing.get().getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Student already has an active membership");
        }

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
        String cashOrderId = "cash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(student.getId())
                .amount(plan.getPrice())
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

        // 11. Publish booking-confirmed Kafka event (best-effort)
        try {
            BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                    .userId(student.getId().toString())
                    .membershipId(membership.getId().toString())
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
}
