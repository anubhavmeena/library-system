package com.library.user.exception;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("User not found: abc"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("abc");
    }

    @Test
    void handleBadRequest_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new IllegalArgumentException("Invalid date format"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Invalid date format");
    }

    @Test
    void handleFileTooLarge_returns413() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleFileTooLarge(new MaxUploadSizeExceededException(5_242_880));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("5MB");
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() throws Exception {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleGeneral(new RuntimeException("unexpected DB error"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("unexpected");
    }

    @Test
    void responseBodyHasTimestamp() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("x"));

        assertThat(resp.getBody().getTimestamp()).isNotBlank();
    }
}
