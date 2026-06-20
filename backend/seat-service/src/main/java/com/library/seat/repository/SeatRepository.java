package com.library.seat.repository;

import com.library.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();

    Optional<Seat> findBySeatNumber(String seatNumber);

    List<Seat> findByRowLabelAndIsActiveTrueOrderBySeatIndexAsc(String rowLabel);

    List<Seat> findByIsActiveFalse();

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.isActive = true")
    long countActiveSeats();
}