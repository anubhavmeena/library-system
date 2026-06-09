package com.library.seat.repository;

import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatBooking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SeatBookingRepositoryTest {

    @Autowired SeatRepository seatRepository;
    @Autowired SeatBookingRepository bookingRepository;

    private Seat seat;
    private final UUID userId      = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();

    private static final LocalDate JAN_01 = LocalDate.of(2025, 1, 1);
    private static final LocalDate JAN_14 = LocalDate.of(2025, 1, 14);
    private static final LocalDate JAN_15 = LocalDate.of(2025, 1, 15);
    private static final LocalDate JAN_31 = LocalDate.of(2025, 1, 31);
    private static final LocalDate FEB_28 = LocalDate.of(2025, 2, 28);

    @BeforeEach
    void setup() {
        seat = seatRepository.save(
                Seat.builder().seatNumber("A1").rowLabel("A").seatIndex(1).isActive(true).build());
    }

    private SeatBooking.SeatBookingBuilder base() {
        return SeatBooking.builder()
                .seat(seat).userId(userId).membershipId(membershipId)
                .shift("MORNING").bookingDate(JAN_01).endDate(JAN_31)
                .status(SeatBooking.Status.ACTIVE);
    }

    // ── findActiveBookingsForShiftAndDate ─────────────────────────────────────

    @Test
    void findActive_matchingShiftAndDate_returned() {
        bookingRepository.save(base().build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("MORNING", JAN_15)).hasSize(1);
    }

    @Test
    void findActive_dateBeforeRange_notReturned() {
        bookingRepository.save(base().build()); // Jan1-Jan31

        // Dec 31 is before the booking range start (Jan 1)
        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("MORNING",
                LocalDate.of(2024, 12, 31))).isEmpty();
    }

    @Test
    void findActive_dateAfterRange_notReturned() {
        bookingRepository.save(base().build()); // Jan1-Jan31

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("MORNING",
                LocalDate.of(2025, 2, 1))).isEmpty();
    }

    @Test
    void findActive_fullDayBooking_returnedForMorningQuery() {
        bookingRepository.save(base().shift("FULL_DAY").build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("MORNING", JAN_15)).hasSize(1);
    }

    @Test
    void findActive_fullDayBooking_returnedForEveningQuery() {
        bookingRepository.save(base().shift("FULL_DAY").build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("EVENING", JAN_15)).hasSize(1);
    }

    @Test
    void findActive_fullDayQuery_returnsAllShifts() {
        // FULL_DAY query should return bookings for any shift
        bookingRepository.save(base().shift("MORNING").build());

        Seat seat2 = seatRepository.save(
                Seat.builder().seatNumber("A2").rowLabel("A").seatIndex(2).isActive(true).build());
        bookingRepository.save(SeatBooking.builder()
                .seat(seat2).userId(userId).membershipId(UUID.randomUUID())
                .shift("EVENING").bookingDate(JAN_01).endDate(JAN_31)
                .status(SeatBooking.Status.ACTIVE).build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("FULL_DAY", JAN_15)).hasSize(2);
    }

    @Test
    void findActive_releasedBooking_excluded() {
        bookingRepository.save(base().status(SeatBooking.Status.RELEASED).build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("MORNING", JAN_15)).isEmpty();
    }

    @Test
    void findActive_wrongShift_notReturned() {
        bookingRepository.save(base().shift("MORNING").build());

        assertThat(bookingRepository.findActiveBookingsForShiftAndDate("EVENING", JAN_15)).isEmpty();
    }

    // ── findBookedSeatIds ─────────────────────────────────────────────────────

    @Test
    void findBookedSeatIds_returnsIdOfBookedSeat() {
        bookingRepository.save(base().build());

        List<UUID> ids = bookingRepository.findBookedSeatIds("MORNING", JAN_15);
        assertThat(ids).containsExactly(seat.getId());
    }

    @Test
    void findBookedSeatIds_noBookings_empty() {
        assertThat(bookingRepository.findBookedSeatIds("MORNING", JAN_15)).isEmpty();
    }

    // ── findByMembershipId ────────────────────────────────────────────────────

    @Test
    void findByMembershipId_found() {
        bookingRepository.save(base().membershipId(membershipId).build());

        assertThat(bookingRepository.findByMembershipId(membershipId)).isPresent();
    }

    @Test
    void findByMembershipId_notFound() {
        assertThat(bookingRepository.findByMembershipId(UUID.randomUUID())).isEmpty();
    }

    // ── findByUserIdAndStatus ─────────────────────────────────────────────────

    @Test
    void findByUserIdAndStatus_returnsOnlyMatchingStatus() {
        bookingRepository.save(base().status(SeatBooking.Status.ACTIVE).build());
        Seat seat2 = seatRepository.save(
                Seat.builder().seatNumber("B1").rowLabel("B").seatIndex(1).isActive(true).build());
        bookingRepository.save(SeatBooking.builder()
                .seat(seat2).userId(userId).membershipId(UUID.randomUUID())
                .shift("MORNING").bookingDate(JAN_01).endDate(JAN_31)
                .status(SeatBooking.Status.RELEASED).build());

        List<SeatBooking> active = bookingRepository.findByUserIdAndStatus(userId, SeatBooking.Status.ACTIVE);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo(SeatBooking.Status.ACTIVE);
    }

    @Test
    void findByUserIdAndStatus_wrongUser_empty() {
        bookingRepository.save(base().build());
        assertThat(bookingRepository.findByUserIdAndStatus(UUID.randomUUID(), SeatBooking.Status.ACTIVE))
                .isEmpty();
    }

    // ── existsBySeatId... conflict check ─────────────────────────────────────

    @Test
    void existsConflict_overlappingRange_true() {
        // Existing: Jan1-Jan31, Requested: Jan15-Feb28 → overlap
        bookingRepository.save(base().bookingDate(JAN_01).endDate(JAN_31).build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        FEB_28, // endDate of requested booking
                        JAN_15  // startDate of requested booking
                );
        assertThat(conflict).isTrue();
    }

    @Test
    void existsConflict_noOverlap_false() {
        // Existing: Jan1-Jan14, Requested: Jan15-Feb28 → no overlap (adjacent, not overlapping)
        bookingRepository.save(base().bookingDate(JAN_01).endDate(JAN_14).build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        FEB_28, JAN_15
                );
        assertThat(conflict).isFalse();
    }

    @Test
    void existsConflict_releasedBooking_false() {
        bookingRepository.save(base().status(SeatBooking.Status.RELEASED).build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        JAN_31, JAN_01
                );
        assertThat(conflict).isFalse();
    }

    @Test
    void existsConflict_differentShift_false() {
        bookingRepository.save(base().shift("EVENING").build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        JAN_31, JAN_01
                );
        assertThat(conflict).isFalse();
    }

    @Test
    void existsConflict_differentSeat_false() {
        bookingRepository.save(base().build()); // on `seat`

        Seat otherSeat = seatRepository.save(
                Seat.builder().seatNumber("B2").rowLabel("B").seatIndex(2).isActive(true).build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        otherSeat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        JAN_31, JAN_01
                );
        assertThat(conflict).isFalse();
    }

    @Test
    void existsConflict_exactSameDates_true() {
        bookingRepository.save(base().bookingDate(JAN_01).endDate(JAN_31).build());

        boolean conflict = bookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), "MORNING", SeatBooking.Status.ACTIVE,
                        JAN_31, JAN_01
                );
        assertThat(conflict).isTrue();
    }
}
