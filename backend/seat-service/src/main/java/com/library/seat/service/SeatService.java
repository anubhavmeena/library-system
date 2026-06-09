package com.library.seat.service;

import com.library.seat.dto.*;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatBooking;
import com.library.seat.exception.ResourceNotFoundException;
import com.library.seat.exception.SeatAlreadyBookedException;
import com.library.seat.repository.SeatBookingRepository;
import com.library.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository        seatRepository;
    private final SeatBookingRepository seatBookingRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.seat.cache-ttl:300}")
    private long cacheTtlSeconds;

    // ── Get Seat Availability ─────────────────────────────────────────────────

    public SeatAvailabilityDto getAvailability(String shift, String dateStr) {
        String    resolvedShift = resolveShift(shift);
        LocalDate date          = (dateStr != null) ? LocalDate.parse(dateStr) : LocalDate.now();

        // 1. Try Redis cache first
        String cacheKey = "seats:availability:" + resolvedShift + ":" + date;
        Object cached   = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof SeatAvailabilityDto dto) {
            log.debug("Seat availability cache hit: {}", cacheKey);
            return dto;
        }

        // 2. Fetch all 110 active seats from DB
        List<Seat> allSeats  = seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();

        // 3. Fetch only the IDs of booked seats (efficient — no JOIN)
        Set<UUID>  bookedIds = new HashSet<>(
                seatBookingRepository.findBookedSeatIds(resolvedShift, date)
        );

        // 4. Map each seat to a DTO with isBooked flag
        List<SeatDto> seatDtos = allSeats.stream()
                .map(s -> SeatDto.fromEntity(s, bookedIds.contains(s.getId())))
                .collect(Collectors.toList());

        // 5. Group by row for the seat grid display (A -> [...28 seats], etc.)
        Map<String, List<SeatDto>> byRow = seatDtos.stream()
                .collect(Collectors.groupingBy(
                        SeatDto::getRowLabel,
                        LinkedHashMap::new,     // preserves A, B, C, D insertion order
                        Collectors.toList()
                ));

        long bookedCount = seatDtos.stream().filter(SeatDto::isBooked).count();

        SeatAvailabilityDto result = SeatAvailabilityDto.builder()
                .shift(resolvedShift)
                .date(date.toString())
                .totalSeats(seatDtos.size())
                .bookedSeats((int) bookedCount)
                .availableSeats((int) (seatDtos.size() - bookedCount))
                .seats(seatDtos)
                .seatsByRow(byRow)
                .build();

        // 6. Cache for 5 minutes (cache-ttl configured in application.yml)
        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(cacheTtlSeconds));
        return result;
    }

    // ── Book a Seat ───────────────────────────────────────────────────────────

    @Transactional
    public SeatBookingDto bookSeat(String userId, BookSeatRequest request) {
        // Resolve seat from DB
        Seat seat = seatRepository.findBySeatNumber(request.getSeatNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat not found: " + request.getSeatNumber()));

        if (!Boolean.TRUE.equals(seat.getIsActive())) {
            throw new IllegalArgumentException(
                    "Seat " + request.getSeatNumber() + " is not available.");
        }

        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate   = LocalDate.parse(request.getEndDate());
        String    shift     = resolveShift(request.getShift());

        // Check for overlapping booking conflict
        boolean conflict = seatBookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        seat.getId(), shift, SeatBooking.Status.ACTIVE, endDate, startDate
                );

        if (conflict) {
            throw new SeatAlreadyBookedException(request.getSeatNumber(), shift);
        }

        // Persist the booking
        SeatBooking booking = SeatBooking.builder()
                .seat(seat)
                .userId(UUID.fromString(userId))
                .membershipId(UUID.fromString(request.getMembershipId()))
                .shift(shift)
                .bookingDate(startDate)
                .endDate(endDate)
                .status(SeatBooking.Status.ACTIVE)
                .build();

        SeatBooking saved = seatBookingRepository.save(booking);

        // Bust Redis cache so next availability query reflects this booking
        invalidateCache(shift, startDate, endDate);

        log.info("Seat {} booked by user {} for {} shift ({} → {})",
                request.getSeatNumber(), userId, shift, startDate, endDate);

        return SeatBookingDto.fromEntity(saved);
    }

    // ── Release a Seat ────────────────────────────────────────────────────────

    @Transactional
    public void releaseSeat(String membershipId) {
        seatBookingRepository.findByMembershipId(UUID.fromString(membershipId))
                .ifPresent(booking -> {
                    booking.setStatus(SeatBooking.Status.RELEASED);
                    seatBookingRepository.save(booking);
                    invalidateCache(booking.getShift(),
                            booking.getBookingDate(), booking.getEndDate());
                    log.info("Seat {} released for membership {}",
                            booking.getSeat().getSeatNumber(), membershipId);
                });
    }

    // ── Get My Active Bookings ────────────────────────────────────────────────

    public List<SeatBookingDto> getMyBookings(String userId) {
        return seatBookingRepository
                .findByUserIdAndStatus(UUID.fromString(userId), SeatBooking.Status.ACTIVE)
                .stream()
                .map(SeatBookingDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Admin: All Bookings for a Shift + Date ────────────────────────────────

    public List<SeatBookingDto> getBookingsForShiftAndDate(String shift, String dateStr) {
        LocalDate date = (dateStr != null) ? LocalDate.parse(dateStr) : LocalDate.now();
        return seatBookingRepository
                .findActiveBookingsForShiftAndDate(resolveShift(shift), date)
                .stream()
                .map(SeatBookingDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveShift(String shift) {
        if (shift == null) return "FULL_DAY";
        return switch (shift.toUpperCase()) {
            case "MORNING" -> "MORNING";
            case "EVENING" -> "EVENING";
            default        -> "FULL_DAY";
        };
    }

    private void invalidateCache(String shift, LocalDate start, LocalDate end) {
        // Invalidate every date in the booking range for this shift
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            redisTemplate.delete("seats:availability:" + shift + ":" + cursor);
            // If a FULL_DAY booking is created, also bust MORNING and EVENING caches
            // since a FULL_DAY seat counts as booked for both sub-shifts
            if ("FULL_DAY".equals(shift)) {
                redisTemplate.delete("seats:availability:MORNING:" + cursor);
                redisTemplate.delete("seats:availability:EVENING:" + cursor);
            }
            cursor = cursor.plusDays(1);
        }
    }
}