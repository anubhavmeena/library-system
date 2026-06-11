package com.library.admin.repository;

import com.library.admin.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    Optional<Seat> findBySeatNumber(String seatNumber);
}
