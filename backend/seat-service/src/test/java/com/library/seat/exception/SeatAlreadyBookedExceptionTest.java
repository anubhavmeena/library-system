package com.library.seat.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SeatAlreadyBookedExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new SeatAlreadyBookedException("A1", "MORNING")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void messageContainsSeatNumber() {
        assertThat(new SeatAlreadyBookedException("A1", "MORNING").getMessage()).contains("A1");
    }

    @Test
    void messageContainsShift() {
        assertThat(new SeatAlreadyBookedException("A1", "MORNING").getMessage()).contains("MORNING");
    }

    @Test
    void messageContainsAlreadyBooked() {
        assertThat(new SeatAlreadyBookedException("B14", "EVENING").getMessage())
                .contains("already booked");
    }

    @Test
    void differentSeatAndShift_differentMessages() {
        String msg1 = new SeatAlreadyBookedException("A1", "MORNING").getMessage();
        String msg2 = new SeatAlreadyBookedException("D26", "EVENING").getMessage();
        assertThat(msg1).isNotEqualTo(msg2);
    }
}
