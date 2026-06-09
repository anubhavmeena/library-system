package com.library.membership.exception;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("Plan not found: abc"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Plan not found: abc");
    }

    @Test
    void handleBadRequest_illegalArgument_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new IllegalArgumentException("Invalid shift: FULL_DAY for half-day plan"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("shift");
    }

    @Test
    void handleRuntime_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleRuntime(new RuntimeException("Payment gateway error"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Payment gateway error");
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleGeneral(new Exception("DB connection lost"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("unexpected");
    }

    @Test
    void handleNotFound_bodyContainsTimestamp() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("x"));

        assertThat(resp.getBody().getTimestamp()).isNotBlank();
    }
}
