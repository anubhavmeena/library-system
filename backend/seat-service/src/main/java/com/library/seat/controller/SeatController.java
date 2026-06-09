package com.library.seat.controller;

import com.library.seat.dto.*;
import com.library.seat.service.SeatService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SeatController {

    private final SeatService seatService;

    // ── Seat Availability ─────────────────────────────────────────────────────
    // Called by BookingPage.jsx to render the 110-seat cinema grid.
    // Returns all seats with isBooked = true/false for the requested shift + date.
    // Response is Redis-cached for 5 minutes.
    // GET /api/seats/availability?shift=MORNING&date=2024-12-01

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<SeatAvailabilityDto>> getAvailability(
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(
                ApiResponse.success(seatService.getAvailability(shift, date)));
    }

    // ── Book a Seat ───────────────────────────────────────────────────────────
    // Called by BookingPage.jsx after Razorpay payment is verified.
    // POST /api/seats/book

    @PostMapping("/book")
    public ResponseEntity<ApiResponse<SeatBookingDto>> bookSeat(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody BookSeatRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(seatService.bookSeat(userId, request)));
    }

    // ── Release a Seat ────────────────────────────────────────────────────────
    // Called by admin when a membership is cancelled.
    // DELETE /api/seats/release/{membershipId}

    @DeleteMapping("/release/{membershipId}")
    public ResponseEntity<ApiResponse<String>> releaseSeat(
            @PathVariable String membershipId,
            @RequestHeader("X-User-Role") String role) {
        seatService.releaseSeat(membershipId);
        return ResponseEntity.ok(ApiResponse.success("Seat released successfully"));
    }

    // ── My Active Bookings ────────────────────────────────────────────────────
    // Returns the student's current active seat booking.
    // GET /api/seats/my

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<SeatBookingDto>>> getMyBookings(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.success(seatService.getMyBookings(userId)));
    }

    // ── Admin: Bookings for Shift + Date ──────────────────────────────────────
    // Used by AdminSeatsPage.jsx to populate the admin seat occupancy map.
    // GET /api/seats/admin/bookings?shift=MORNING&date=2024-12-01

    @GetMapping("/admin/bookings")
    public ResponseEntity<ApiResponse<List<SeatBookingDto>>> getBookingsForShift(
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String date,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(
                ApiResponse.success(seatService.getBookingsForShiftAndDate(shift, date)));
    }
}