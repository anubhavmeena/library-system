package com.library.seat.repository;

import com.library.seat.entity.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SeatRepositoryTest {

    @Autowired
    SeatRepository seatRepository;

    private Seat.SeatBuilder base(String seatNumber, String row, int idx, boolean active) {
        return Seat.builder().seatNumber(seatNumber).rowLabel(row).seatIndex(idx).isActive(active);
    }

    // ── findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc ──────────────────────

    @Test
    void findActive_returnsOnlyActiveSeats() {
        seatRepository.save(base("A1", "A", 1, true).build());
        seatRepository.save(base("A2", "A", 2, false).build());
        seatRepository.save(base("B1", "B", 1, true).build());

        List<Seat> active = seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(s -> Boolean.TRUE.equals(s.getIsActive()));
    }

    @Test
    void findActive_orderedByRowThenIndex() {
        seatRepository.save(base("B2", "B", 2, true).build());
        seatRepository.save(base("A1", "A", 1, true).build());
        seatRepository.save(base("B1", "B", 1, true).build());

        List<Seat> results = seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();

        assertThat(results.get(0).getSeatNumber()).isEqualTo("A1");
        assertThat(results.get(1).getSeatNumber()).isEqualTo("B1");
        assertThat(results.get(2).getSeatNumber()).isEqualTo("B2");
    }

    @Test
    void findActive_emptyTable_returnsEmpty() {
        assertThat(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).isEmpty();
    }

    // ── findBySeatNumber ──────────────────────────────────────────────────────

    @Test
    void findBySeatNumber_found() {
        seatRepository.save(base("C5", "C", 5, true).build());
        assertThat(seatRepository.findBySeatNumber("C5")).isPresent();
    }

    @Test
    void findBySeatNumber_notFound() {
        assertThat(seatRepository.findBySeatNumber("X99")).isEmpty();
    }

    @Test
    void findBySeatNumber_correctSeatReturned() {
        seatRepository.save(base("A1", "A", 1, true).build());
        seatRepository.save(base("A2", "A", 2, true).build());

        assertThat(seatRepository.findBySeatNumber("A2"))
                .hasValueSatisfying(s -> assertThat(s.getSeatNumber()).isEqualTo("A2"));
    }

    // ── findByRowLabelAndIsActiveTrueOrderBySeatIndexAsc ──────────────────────

    @Test
    void findByRow_returnsOnlyRowAndActiveSeats() {
        seatRepository.save(base("A1", "A", 1, true).build());
        seatRepository.save(base("A2", "A", 2, false).build()); // inactive
        seatRepository.save(base("B1", "B", 1, true).build()); // different row

        List<Seat> rowA = seatRepository.findByRowLabelAndIsActiveTrueOrderBySeatIndexAsc("A");

        assertThat(rowA).hasSize(1);
        assertThat(rowA.get(0).getSeatNumber()).isEqualTo("A1");
    }

    // ── countActiveSeats ──────────────────────────────────────────────────────

    @Test
    void countActiveSeats_countsOnlyActive() {
        seatRepository.save(base("A1", "A", 1, true).build());
        seatRepository.save(base("A2", "A", 2, true).build());
        seatRepository.save(base("B1", "B", 1, false).build());

        assertThat(seatRepository.countActiveSeats()).isEqualTo(2);
    }

    @Test
    void countActiveSeats_emptyTable_returnsZero() {
        assertThat(seatRepository.countActiveSeats()).isZero();
    }
}
