package com.library.admin.dto;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_setsSuccessTrueAndData() {
        ApiResponse<String> resp = ApiResponse.success("hello");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).isEqualTo("hello");
        assertThat(resp.getMessage()).isNull();
    }

    @Test
    void success_timestampIsIso8601() {
        ApiResponse<String> resp = ApiResponse.success("x");

        assertThat(resp.getTimestamp()).isNotBlank();
        // Must parse as an Instant without throwing
        assertThatCode(() -> Instant.parse(resp.getTimestamp())).doesNotThrowAnyException();
    }

    @Test
    void error_setsSuccessFalseAndMessage() {
        ApiResponse<Void> resp = ApiResponse.error("something went wrong");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getMessage()).isEqualTo("something went wrong");
        assertThat(resp.getData()).isNull();
    }

    @Test
    void error_timestampIsPopulated() {
        ApiResponse<Void> resp = ApiResponse.error("err");

        assertThat(resp.getTimestamp()).isNotBlank();
        assertThatCode(() -> Instant.parse(resp.getTimestamp())).doesNotThrowAnyException();
    }

    @Test
    void success_withNullData_isValid() {
        ApiResponse<Object> resp = ApiResponse.success(null);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).isNull();
        assertThat(resp.getTimestamp()).isNotBlank();
    }

    @Test
    void success_withComplexData_preservesObject() {
        DashboardDto dto = DashboardDto.builder()
                .totalStudents(10L)
                .totalSeats(110L)
                .build();

        ApiResponse<DashboardDto> resp = ApiResponse.success(dto);

        assertThat(resp.getData()).isSameAs(dto);
        assertThat(resp.getData().getTotalStudents()).isEqualTo(10L);
    }
}
