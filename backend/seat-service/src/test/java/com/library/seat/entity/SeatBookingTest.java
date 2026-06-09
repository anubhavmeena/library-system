package com.library.seat.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatBookingTest {

    private Seat seat() {
        return Seat.builder().id(UUID.randomUUID()).seatNumber("A1")
                .rowLabel("A").seatIndex(1).isActive(true).build();
    }

    @Test
    void builder_setsAllFields() {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        SeatBooking booking = SeatBooking.builder()
                .id(UUID.randomUUID())
                .seat(seat())
                .userId(userId)
                .membershipId(membershipId)
                .shift("MORNING")
                .bookingDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(SeatBooking.Status.ACTIVE)
                .build();

        assertThat(booking.getUserId()).isEqualTo(userId);
        assertThat(booking.getMembershipId()).isEqualTo(membershipId);
        assertThat(booking.getShift()).isEqualTo("MORNING");
        assertThat(booking.getBookingDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(booking.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(booking.getStatus()).isEqualTo(SeatBooking.Status.ACTIVE);
    }

    @Test
    void builder_withoutStatus_statusIsNull() {
        // @Builder ignores field initializer `private Status status = Status.ACTIVE`
        SeatBooking booking = SeatBooking.builder().shift("MORNING").build();
        assertThat(booking.getStatus()).isNull();
    }

    @Test
    void noArgsConstructor_statusIsActive() {
        // new SeatBooking() runs field initializer — status = ACTIVE
        assertThat(new SeatBooking().getStatus()).isEqualTo(SeatBooking.Status.ACTIVE);
    }

    @Test
    void onCreate_setsCreatedAt() throws Exception {
        SeatBooking booking = new SeatBooking();
        assertThat(booking.getCreatedAt()).isNull();

        Method onCreate = SeatBooking.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(booking);

        assertThat(booking.getCreatedAt()).isNotNull().isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void statusEnum_allValuesExist() {
        assertThat(SeatBooking.Status.values())
                .containsExactlyInAnyOrder(
                        SeatBooking.Status.ACTIVE,
                        SeatBooking.Status.RELEASED,
                        SeatBooking.Status.EXPIRED
                );
    }
}
