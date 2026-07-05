package com.library.admin.repository;

import com.library.admin.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    Optional<Seat> findBySeatNumber(String seatNumber);

    // Used by getSeatMap() — the real seat layout, so the admin dashboard grid
    // can never drift from what seat-service/createCashMembership actually
    // treat as bookable (see incident: a hardcoded row-count literal here used
    // to hide two genuinely-occupied seats that fell outside it).
    List<Seat> findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();
}
