package com.library.admin.repository;

import com.library.admin.entity.SeatBooking;
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

    @Query("""
        SELECT b FROM SeatBooking b
        WHERE b.seatId = :seatId
          AND b.status = 'ACTIVE'
          AND b.bookingDate <= :endDate
          AND b.endDate >= :startDate
        """)
    List<SeatBooking> findActiveBookingsForSeat(
            @Param("seatId")    UUID      seatId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    Optional<SeatBooking> findFirstByMembershipIdAndStatus(UUID membershipId, SeatBooking.Status status);
}
