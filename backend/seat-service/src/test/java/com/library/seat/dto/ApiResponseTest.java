package com.library.seat.dto;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApiResponseTest {

    @Test
    void success_isSuccessTrue() {
        ApiResponse<String> resp = ApiResponse.success("hello");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void success_dataSet() {
        ApiResponse<Integer> resp = ApiResponse.success(42);
        assertThat(resp.getData()).isEqualTo(42);
    }

    @Test
    void success_timestampIsIso8601() {
        ApiResponse<Void> resp = ApiResponse.success(null);
        assertThatCode(() -> Instant.parse(resp.getTimestamp())).doesNotThrowAnyException();
    }

    @Test
    void error_isSuccessFalse() {
        ApiResponse<Void> resp = ApiResponse.error("Something went wrong");
        assertThat(resp.isSuccess()).isFalse();
    }

    @Test
    void error_messageSet() {
        ApiResponse<Void> resp = ApiResponse.error("Seat conflict");
        assertThat(resp.getMessage()).isEqualTo("Seat conflict");
    }

    @Test
    void error_dataIsNull() {
        ApiResponse<Void> resp = ApiResponse.error("err");
        assertThat(resp.getData()).isNull();
    }

    @Test
    void success_messageIsNull() {
        ApiResponse<String> resp = ApiResponse.success("data");
        assertThat(resp.getMessage()).isNull();
    }
}
