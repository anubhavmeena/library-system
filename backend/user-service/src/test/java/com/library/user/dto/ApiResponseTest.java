package com.library.user.dto;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_isSuccessTrue() {
        assertThat(ApiResponse.success("data").isSuccess()).isTrue();
    }

    @Test
    void success_dataSet() {
        assertThat(ApiResponse.success(42).getData()).isEqualTo(42);
    }

    @Test
    void success_timestampIsIso8601() {
        assertThatCode(() -> Instant.parse(ApiResponse.success(null).getTimestamp()))
                .doesNotThrowAnyException();
    }

    @Test
    void success_messageIsNull() {
        assertThat(ApiResponse.success("anything").getMessage()).isNull();
    }

    @Test
    void error_isSuccessFalse() {
        assertThat(ApiResponse.error("something").isSuccess()).isFalse();
    }

    @Test
    void error_messageSet() {
        assertThat(ApiResponse.error("Email already in use").getMessage())
                .isEqualTo("Email already in use");
    }

    @Test
    void error_dataIsNull() {
        assertThat(ApiResponse.error("err").getData()).isNull();
    }
}
