package com.library.seat.exception;

import com.library.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("Seat not found: X99"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("X99");
    }

    @Test
    void handleSeatBooked_returns409() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleSeatBooked(new SeatAlreadyBookedException("A1", "MORNING"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("A1");
    }

    @Test
    void handleBadRequest_illegalArgument_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBadRequest(new IllegalArgumentException("Seat A1 is not available"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("not available");
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() throws Exception {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleGeneral(new Exception("DB connection lost"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("unexpected");
    }

    @Test
    void responseBodyContainsTimestamp() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleNotFound(new ResourceNotFoundException("x"));

        assertThat(resp.getBody().getTimestamp()).isNotBlank();
    }
}
