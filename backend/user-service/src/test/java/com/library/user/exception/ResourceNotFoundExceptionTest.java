package com.library.user.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new ResourceNotFoundException("msg")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void messagePreserved() {
        assertThat(new ResourceNotFoundException("User not found: abc").getMessage())
                .isEqualTo("User not found: abc");
    }

    @Test
    void canBeThrownAndCaught() {
        assertThatThrownBy(() -> { throw new ResourceNotFoundException("test"); })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test");
    }

    @Test
    void emptyMessage() {
        assertThat(new ResourceNotFoundException("").getMessage()).isEqualTo("");
    }
}
