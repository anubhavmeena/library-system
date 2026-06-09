package com.library.seat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.seat.dto.*;
import com.library.seat.exception.*;
import com.library.seat.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
@Import(GlobalExceptionHandler.class)
class SeatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  SeatService seatService;
    @Autowired ObjectMapper objectMapper;

    private BookSeatRequest validRequest() {
        BookSeatRequest r = new BookSeatRequest();
        r.setSeatNumber("A1");
        r.setMembershipId(UUID.randomUUID().toString());
        r.setShift("MORNING");
        r.setStartDate("2025-01-01");
        r.setEndDate("2025-01-31");
        return r;
    }

    private SeatAvailabilityDto emptyAvailability(String shift) {
        return SeatAvailabilityDto.builder()
                .shift(shift).date("2025-01-01").totalSeats(110)
                .bookedSeats(0).availableSeats(110)
                .seats(List.of()).seatsByRow(Map.of()).build();
    }

    private SeatBookingDto sampleBookingDto() {
        return SeatBookingDto.builder()
                .id(UUID.randomUUID().toString())
                .seatNumber("A1").rowLabel("A").userId(UUID.randomUUID().toString())
                .membershipId(UUID.randomUUID().toString()).shift("MORNING")
                .bookingDate("2025-01-01").endDate("2025-01-31").status("ACTIVE")
                .build();
    }

    // ── GET /api/seats/availability ───────────────────────────────────────────

    @Test
    void getAvailability_noParams_returns200() throws Exception {
        when(seatService.getAvailability(null, null)).thenReturn(emptyAvailability("FULL_DAY"));

        mockMvc.perform(get("/api/seats/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shift").value("FULL_DAY"))
                .andExpect(jsonPath("$.data.totalSeats").value(110));
    }

    @Test
    void getAvailability_withParams_passesThrough() throws Exception {
        when(seatService.getAvailability("MORNING", "2025-01-15"))
                .thenReturn(emptyAvailability("MORNING"));

        mockMvc.perform(get("/api/seats/availability")
                .param("shift", "MORNING")
                .param("date", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift").value("MORNING"));
    }

    // ── POST /api/seats/book ──────────────────────────────────────────────────

    @Test
    void bookSeat_valid_returns200WithDto() throws Exception {
        when(seatService.bookSeat(any(), any())).thenReturn(sampleBookingDto());

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void bookSeat_missingXUserIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/seats/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void bookSeat_blankSeatNumber_returns400() throws Exception {
        BookSeatRequest r = validRequest();
        r.setSeatNumber("");

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookSeat_nullMembershipId_returns400() throws Exception {
        BookSeatRequest r = validRequest();
        r.setMembershipId(null);

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookSeat_seatNotFound_returns404() throws Exception {
        when(seatService.bookSeat(any(), any()))
                .thenThrow(new ResourceNotFoundException("Seat X99 not found"));

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void bookSeat_seatAlreadyBooked_returns409() throws Exception {
        when(seatService.bookSeat(any(), any()))
                .thenThrow(new SeatAlreadyBookedException("A1", "MORNING"));

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("A1")));
    }

    @Test
    void bookSeat_illegalArgument_returns400() throws Exception {
        when(seatService.bookSeat(any(), any()))
                .thenThrow(new IllegalArgumentException("Seat A1 is not available"));

        mockMvc.perform(post("/api/seats/book")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/seats/release/{membershipId} ──────────────────────────────

    @Test
    void releaseSeat_valid_returns200() throws Exception {
        doNothing().when(seatService).releaseSeat(any());

        mockMvc.perform(delete("/api/seats/release/" + UUID.randomUUID())
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Seat released successfully"));
    }

    @Test
    void releaseSeat_missingRoleHeader_returns400() throws Exception {
        mockMvc.perform(delete("/api/seats/release/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/seats/my ─────────────────────────────────────────────────────

    @Test
    void getMyBookings_withHeader_returns200() throws Exception {
        when(seatService.getMyBookings(any())).thenReturn(List.of(sampleBookingDto()));

        mockMvc.perform(get("/api/seats/my")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMyBookings_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/seats/my"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/seats/admin/bookings ─────────────────────────────────────────

    @Test
    void getAdminBookings_withRole_returns200() throws Exception {
        when(seatService.getBookingsForShiftAndDate(any(), any()))
                .thenReturn(List.of(sampleBookingDto()));

        mockMvc.perform(get("/api/seats/admin/bookings")
                .header("X-User-Role", "ADMIN")
                .param("shift", "MORNING")
                .param("date", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAdminBookings_missingRoleHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/seats/admin/bookings"))
                .andExpect(status().isBadRequest());
    }
}
