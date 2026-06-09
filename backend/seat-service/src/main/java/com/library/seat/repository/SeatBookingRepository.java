package com.library.seat.repository;

import com.library.seat.entity.SeatBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatBookingRepository extends JpaRepository<SeatBooking, UUID> {

    // All active bookings overlapping a given date for a given shift
    @Query("""
        SELECT sb FROM SeatBooking sb
        WHERE sb.status = 'ACTIVE'
          AND sb.bookingDate <= :date
          AND sb.endDate    >= :date
          AND (sb.shift = :shift OR sb.shift = 'FULL_DAY' OR :shift = 'FULL_DAY')
        """)
    List<SeatBooking> findActiveBookingsForShiftAndDate(
            @Param("shift") String shift,
            @Param("date")  LocalDate date
    );

    // Returns only booked seat UUIDs — efficient for the 110-seat availability grid
    @Query("""
        SELECT sb.seat.id FROM SeatBooking sb
        WHERE sb.status = 'ACTIVE'
          AND sb.bookingDate <= :date
          AND sb.endDate    >= :date
          AND (sb.shift = :shift OR sb.shift = 'FULL_DAY' OR :shift = 'FULL_DAY')
        """)
    List<UUID> findBookedSeatIds(
            @Param("shift") String shift,
            @Param("date")  LocalDate date
    );

    Optional<SeatBooking> findByMembershipId(UUID membershipId);

    List<SeatBooking> findByUserIdAndStatus(UUID userId, SeatBooking.Status status);

    // Conflict check — used before creating a new booking
    boolean existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID seatId, String shift, SeatBooking.Status status,
            LocalDate bookingDate, LocalDate endDate
    );
}