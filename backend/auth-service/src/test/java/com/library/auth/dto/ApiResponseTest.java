package com.library.auth.dto;

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
        assertThat(resp.getTimestamp()).isNotBlank();
    }

    @Test
    void error_setsSuccessFalseAndMessage() {
        ApiResponse<Void> resp = ApiResponse.error("something went wrong");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getMessage()).isEqualTo("something went wrong");
        assertThat(resp.getData()).isNull();
        assertThat(resp.getTimestamp()).isNotBlank();
    }

    @Test
    void success_withNullData_stillValid() {
        ApiResponse<String> resp = ApiResponse.success(null);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).isNull();
    }

    @Test
    void success_withComplexData_preservesData() {
        UserInfoDto user = UserInfoDto.builder()
                .id("uuid-123")
                .name("Alice")
                .role("STUDENT")
                .build();
        ApiResponse<UserInfoDto> resp = ApiResponse.success(user);

        assertThat(resp.getData().getId()).isEqualTo("uuid-123");
        assertThat(resp.getData().getName()).isEqualTo("Alice");
    }

    @Test
    void timestamp_isIso8601Parseable() {
        ApiResponse<String> resp = ApiResponse.success("data");

        assertThatCode(() -> Instant.parse(resp.getTimestamp()))
                .doesNotThrowAnyException();
    }

    @Test
    void twoInstances_haveDistinctTimestamps() throws InterruptedException {
        ApiResponse<String> first = ApiResponse.success("a");
        Thread.sleep(5);
        ApiResponse<String> second = ApiResponse.success("b");

        Instant t1 = Instant.parse(first.getTimestamp());
        Instant t2 = Instant.parse(second.getTimestamp());
        assertThat(t2).isAfterOrEqualTo(t1);
    }
}
