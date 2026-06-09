package com.library.seat.dto;

import com.library.seat.entity.Seat;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatDtoTest {

    private Seat buildSeat(Boolean isActive) {
        return Seat.builder()
                .id(UUID.randomUUID())
                .seatNumber("A1")
                .rowLabel("A")
                .seatIndex(1)
                .isActive(isActive)
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        Seat seat = buildSeat(true);
        SeatDto dto = SeatDto.fromEntity(seat, false);

        assertThat(dto.getId()).isEqualTo(seat.getId().toString());
        assertThat(dto.getSeatNumber()).isEqualTo("A1");
        assertThat(dto.getRowLabel()).isEqualTo("A");
        assertThat(dto.getSeatIndex()).isEqualTo(1);
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.isBooked()).isFalse();
    }

    @Test
    void fromEntity_bookedTrue_isBookedTrue() {
        SeatDto dto = SeatDto.fromEntity(buildSeat(true), true);
        assertThat(dto.isBooked()).isTrue();
    }

    @Test
    void fromEntity_bookedFalse_isBookedFalse() {
        SeatDto dto = SeatDto.fromEntity(buildSeat(true), false);
        assertThat(dto.isBooked()).isFalse();
    }

    @Test
    void fromEntity_isActiveTrue_isActiveTrue() {
        SeatDto dto = SeatDto.fromEntity(buildSeat(true), false);
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void fromEntity_isActiveFalse_isActiveFalse() {
        SeatDto dto = SeatDto.fromEntity(buildSeat(false), false);
        assertThat(dto.isActive()).isFalse();
    }

    @Test
    void fromEntity_isActiveNull_isActiveFalse() {
        // Boolean.TRUE.equals(null) = false
        SeatDto dto = SeatDto.fromEntity(buildSeat(null), false);
        assertThat(dto.isActive()).isFalse();
    }

    @Test
    void fromEntity_idIsString() {
        UUID id = UUID.randomUUID();
        Seat seat = Seat.builder().id(id).seatNumber("B5").rowLabel("B")
                .seatIndex(5).isActive(true).build();

        assertThat(SeatDto.fromEntity(seat, false).getId()).isEqualTo(id.toString());
    }
}
