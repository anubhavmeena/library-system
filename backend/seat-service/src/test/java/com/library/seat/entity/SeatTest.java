package com.library.seat.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatTest {

    @Test
    void builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        Seat seat = Seat.builder()
                .id(id)
                .seatNumber("D26")
                .rowLabel("D")
                .seatIndex(26)
                .isActive(true)
                .build();

        assertThat(seat.getId()).isEqualTo(id);
        assertThat(seat.getSeatNumber()).isEqualTo("D26");
        assertThat(seat.getRowLabel()).isEqualTo("D");
        assertThat(seat.getSeatIndex()).isEqualTo(26);
        assertThat(seat.getIsActive()).isTrue();
    }

    @Test
    void builder_withoutIsActive_isActiveNull() {
        // @Builder ignores the field initializer `private Boolean isActive = true`
        Seat seat = Seat.builder().seatNumber("A1").build();
        assertThat(seat.getIsActive()).isNull();
    }

    @Test
    void noArgsConstructor_isActiveIsTrue() {
        // new Seat() runs field initializers — isActive = true
        Seat seat = new Seat();
        assertThat(seat.getIsActive()).isTrue();
    }

    @Test
    void dataAnnotation_equalsAndHashCode() {
        UUID id = UUID.randomUUID();
        Seat s1 = Seat.builder().id(id).seatNumber("A1").rowLabel("A").seatIndex(1).isActive(true).build();
        Seat s2 = Seat.builder().id(id).seatNumber("A1").rowLabel("A").seatIndex(1).isActive(true).build();

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void dataAnnotation_toString() {
        Seat seat = Seat.builder().seatNumber("B5").rowLabel("B").seatIndex(5).isActive(true).build();
        assertThat(seat.toString()).contains("B5");
    }
}
