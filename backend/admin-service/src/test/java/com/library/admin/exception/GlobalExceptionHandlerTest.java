package com.library.admin.exception;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithErrorBody() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("Student not found: abc"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Student not found: abc");
    }

    @Test
    void handleBadRequest_returns400WithErrorBody() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new IllegalArgumentException("Invalid date format"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Invalid date format");
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleGeneral(new RuntimeException("Unexpected boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("unexpected");
    }
}
