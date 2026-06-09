package com.library.auth.exception;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_runtimeException_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new RuntimeException("OTP expired"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("OTP expired");
    }

    @Test
    void handleNotImplemented_unsupportedOperation_returns501() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotImplemented(new UnsupportedOperationException("Refresh token not implemented yet"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Refresh token not implemented yet");
    }

    @Test
    void handleGeneral_exception_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleGeneral(new Exception("DB connection lost"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("unexpected");
    }

    @Test
    void handleBadRequest_accessDeniedMessage_preservedInResponse() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new RuntimeException("Access denied. Not an admin account."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isEqualTo("Access denied. Not an admin account.");
    }
}
