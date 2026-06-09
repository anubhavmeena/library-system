package com.library.seat.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeatAvailabilityDtoTest {

    @Test
    void builder_allFieldsSet() {
        SeatDto seatDto = SeatDto.builder()
                .seatNumber("A1").rowLabel("A").seatIndex(1)
                .isBooked(false).isActive(true).build();

        SeatAvailabilityDto dto = SeatAvailabilityDto.builder()
                .shift("MORNING")
                .date("2025-01-01")
                .totalSeats(110)
                .bookedSeats(5)
                .availableSeats(105)
                .seats(List.of(seatDto))
                .seatsByRow(Map.of("A", List.of(seatDto)))
                .build();

        assertThat(dto.getShift()).isEqualTo("MORNING");
        assertThat(dto.getDate()).isEqualTo("2025-01-01");
        assertThat(dto.getTotalSeats()).isEqualTo(110);
        assertThat(dto.getBookedSeats()).isEqualTo(5);
        assertThat(dto.getAvailableSeats()).isEqualTo(105);
        assertThat(dto.getSeats()).hasSize(1);
        assertThat(dto.getSeatsByRow()).containsKey("A");
    }

    @Test
    void noArgsConstructor_intFieldsAreZero() {
        SeatAvailabilityDto dto = new SeatAvailabilityDto();
        assertThat(dto.getTotalSeats()).isZero();
        assertThat(dto.getBookedSeats()).isZero();
        assertThat(dto.getAvailableSeats()).isZero();
    }

    @Test
    void noArgsConstructor_referenceFieldsNull() {
        SeatAvailabilityDto dto = new SeatAvailabilityDto();
        assertThat(dto.getShift()).isNull();
        assertThat(dto.getDate()).isNull();
        assertThat(dto.getSeats()).isNull();
        assertThat(dto.getSeatsByRow()).isNull();
    }

    @Test
    void builder_emptySeats_zeroCount() {
        SeatAvailabilityDto dto = SeatAvailabilityDto.builder()
                .shift("FULL_DAY").date("2025-01-01")
                .totalSeats(0).bookedSeats(0).availableSeats(0)
                .seats(List.of()).seatsByRow(Map.of())
                .build();

        assertThat(dto.getSeats()).isEmpty();
        assertThat(dto.getSeatsByRow()).isEmpty();
    }
}
