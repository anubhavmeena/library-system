package com.library.seat.dto;

import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatBooking;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatBookingDtoTest {

    private SeatBooking buildBooking(SeatBooking.Status status) {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Seat seat = Seat.builder()
                .id(UUID.randomUUID())
                .seatNumber("A1")
                .rowLabel("A")
                .seatIndex(1)
                .isActive(true)
                .build();

        return SeatBooking.builder()
                .id(UUID.randomUUID())
                .seat(seat)
                .userId(userId)
                .membershipId(membershipId)
                .shift("MORNING")
                .bookingDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(status)
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        SeatBooking booking = buildBooking(SeatBooking.Status.ACTIVE);
        SeatBookingDto dto = SeatBookingDto.fromEntity(booking);

        assertThat(dto.getId()).isEqualTo(booking.getId().toString());
        assertThat(dto.getSeatId()).isEqualTo(booking.getSeat().getId().toString());
        assertThat(dto.getSeatNumber()).isEqualTo("A1");
        assertThat(dto.getRowLabel()).isEqualTo("A");
        assertThat(dto.getUserId()).isEqualTo(booking.getUserId().toString());
        assertThat(dto.getMembershipId()).isEqualTo(booking.getMembershipId().toString());
        assertThat(dto.getShift()).isEqualTo("MORNING");
        assertThat(dto.getBookingDate()).isEqualTo("2025-01-01");
        assertThat(dto.getEndDate()).isEqualTo("2025-01-31");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void fromEntity_statusActive_mappedCorrectly() {
        assertThat(SeatBookingDto.fromEntity(buildBooking(SeatBooking.Status.ACTIVE)).getStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    void fromEntity_statusReleased_mappedCorrectly() {
        assertThat(SeatBookingDto.fromEntity(buildBooking(SeatBooking.Status.RELEASED)).getStatus())
                .isEqualTo("RELEASED");
    }

    @Test
    void fromEntity_statusExpired_mappedCorrectly() {
        assertThat(SeatBookingDto.fromEntity(buildBooking(SeatBooking.Status.EXPIRED)).getStatus())
                .isEqualTo("EXPIRED");
    }

    @Test
    void fromEntity_datesAreIsoStrings() {
        SeatBookingDto dto = SeatBookingDto.fromEntity(buildBooking(SeatBooking.Status.ACTIVE));
        assertThat(dto.getBookingDate()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(dto.getEndDate()).matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
