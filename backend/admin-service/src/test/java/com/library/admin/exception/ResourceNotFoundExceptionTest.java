package com.library.admin.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new ResourceNotFoundException("msg"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void messageIsPreserved() {
        String msg = "Student not found: abc-123";
        ResourceNotFoundException ex = new ResourceNotFoundException(msg);
        assertThat(ex.getMessage()).isEqualTo(msg);
    }

    @Test
    void canBeThrownAndCaughtAsRuntimeException() {
        assertThatThrownBy(() -> { throw new ResourceNotFoundException("not found"); })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("not found");
    }

    @Test
    void emptyMessageIsAllowed() {
        assertThat(new ResourceNotFoundException("").getMessage()).isEmpty();
    }
}
