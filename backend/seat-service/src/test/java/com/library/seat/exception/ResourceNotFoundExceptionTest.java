package com.library.seat.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new ResourceNotFoundException("msg")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void messagePreserved() {
        assertThat(new ResourceNotFoundException("Seat not found: X99").getMessage())
                .isEqualTo("Seat not found: X99");
    }

    @Test
    void canBeThrownAndCaughtAsRuntimeException() {
        assertThatThrownBy(() -> { throw new ResourceNotFoundException("test"); })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test");
    }

    @Test
    void emptyMessage() {
        assertThat(new ResourceNotFoundException("").getMessage()).isEqualTo("");
    }
}
