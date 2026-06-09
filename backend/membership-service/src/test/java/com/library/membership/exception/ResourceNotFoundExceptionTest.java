package com.library.membership.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new ResourceNotFoundException("msg"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void messagePreserved() {
        assertThat(new ResourceNotFoundException("Plan not found: abc").getMessage())
                .isEqualTo("Plan not found: abc");
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
