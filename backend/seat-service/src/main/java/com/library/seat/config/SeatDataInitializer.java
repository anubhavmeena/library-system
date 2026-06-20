package com.library.seat.config;

import com.library.seat.entity.Seat;
import com.library.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatDataInitializer implements ApplicationRunner {

    private final SeatRepository seatRepository;

    private static final List<String> ROW_ORDER = List.of("A", "B", "C", "D");
    private static final Map<String, Integer> ROW_COUNTS = Map.of(
            "A", 28, "B", 28, "C", 28, "D", 28
    );
    // Physical obstructions (pillars) — seats exist in numbering but are not bookable
    private static final List<String> BLOCKED_SEATS = List.of("B8", "B18");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seatRepository.count() == 0) {
            seedSeats();
        }
        reconcileActiveStatus();
    }

    private void seedSeats() {
        List<Seat> seats = new ArrayList<>();
        for (String row : ROW_ORDER) {
            int count = ROW_COUNTS.get(row);
            for (int i = 1; i <= count; i++) {
                String seatNumber = row + i;
                seats.add(Seat.builder()
                        .seatNumber(seatNumber)
                        .rowLabel(row)
                        .seatIndex(i)
                        .isActive(!BLOCKED_SEATS.contains(seatNumber))
                        .build());
            }
        }
        seatRepository.saveAll(seats);
        log.info("Seeded {} seats (A:28, B:28, C:28, D:28); blocked: {}", seats.size(), BLOCKED_SEATS);
    }

    private void reconcileActiveStatus() {
        List<Seat> inactiveSeats = seatRepository.findByIsActiveFalse();
        for (Seat seat : inactiveSeats) {
            if (!BLOCKED_SEATS.contains(seat.getSeatNumber())) {
                seat.setIsActive(true);
                seatRepository.save(seat);
                log.info("Activated previously blocked seat: {}", seat.getSeatNumber());
            }
        }
    }
}
