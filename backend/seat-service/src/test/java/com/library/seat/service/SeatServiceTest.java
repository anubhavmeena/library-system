package com.library.seat.service;

import com.library.seat.dto.BookSeatRequest;
import com.library.seat.dto.SeatAvailabilityDto;
import com.library.seat.dto.SeatBookingDto;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatBooking;
import com.library.seat.exception.ResourceNotFoundException;
import com.library.seat.exception.SeatAlreadyBookedException;
import com.library.seat.repository.SeatBookingRepository;
import com.library.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock SeatRepository seatRepository;
    @Mock SeatBookingRepository seatBookingRepository;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks SeatService seatService;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null); // cache miss by default
        ReflectionTestUtils.setField(seatService, "cacheTtlSeconds", 300L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Seat seat(String number, String row, int idx) {
        return Seat.builder().id(UUID.randomUUID()).seatNumber(number)
                .rowLabel(row).seatIndex(idx).isActive(true).build();
    }

    private BookSeatRequest req(String seatNumber, String shift, String start, String end) {
        BookSeatRequest r = new BookSeatRequest();
        r.setSeatNumber(seatNumber);
        r.setMembershipId(UUID.randomUUID().toString());
        r.setShift(shift);
        r.setStartDate(start);
        r.setEndDate(end);
        return r;
    }

    private SeatBooking savedBooking(Seat s, String shift, LocalDate start, LocalDate end, String userId) {
        return SeatBooking.builder()
                .id(UUID.randomUUID()).seat(s)
                .userId(UUID.fromString(userId))
                .membershipId(UUID.randomUUID())
                .shift(shift).bookingDate(start).endDate(end)
                .status(SeatBooking.Status.ACTIVE).build();
    }

    private void noConflict() {
        lenient().when(seatBookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        any(), any(), any(), any(), any())).thenReturn(false);
    }

    // ── getAvailability — cache ───────────────────────────────────────────────

    @Test
    void getAvailability_cacheHit_returnsCachedDtoWithoutDbQuery() {
        SeatAvailabilityDto cached = SeatAvailabilityDto.builder()
                .shift("MORNING").date("2025-01-01").totalSeats(110).build();
        when(valueOps.get("seats:availability:MORNING:2025-01-01")).thenReturn(cached);

        SeatAvailabilityDto result = seatService.getAvailability("MORNING", "2025-01-01");

        assertThat(result).isSameAs(cached);
        verify(seatRepository, never()).findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();
    }

    @Test
    void getAvailability_cacheMiss_queriesDbAndCachesResult() {
        Seat a1 = seat("A1", "A", 1);
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of(a1));
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        seatService.getAvailability("MORNING", "2025-01-01");

        verify(seatRepository).findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();
        verify(valueOps).set(eq("seats:availability:MORNING:2025-01-01"),
                any(SeatAvailabilityDto.class), eq(Duration.ofSeconds(300)));
    }

    // ── getAvailability — shift resolution ───────────────────────────────────

    @Test
    void getAvailability_shiftNull_resolvesToFullDay() {
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of());
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability(null, "2025-01-01");

        assertThat(result.getShift()).isEqualTo("FULL_DAY");
        verify(seatBookingRepository).findBookedSeatIds(eq("FULL_DAY"), any());
    }

    @Test
    void getAvailability_shiftMorning_resolvedCorrectly() {
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of());
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability("MORNING", "2025-01-01");

        assertThat(result.getShift()).isEqualTo("MORNING");
    }

    @Test
    void getAvailability_shiftLowercase_resolvedCorrectly() {
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of());
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability("morning", "2025-01-01");

        assertThat(result.getShift()).isEqualTo("MORNING");
    }

    @Test
    void getAvailability_shiftUnknown_resolvesToFullDay() {
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of());
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability("WEEKEND", "2025-01-01");

        assertThat(result.getShift()).isEqualTo("FULL_DAY");
    }

    // ── getAvailability — date handling ──────────────────────────────────────

    @Test
    void getAvailability_dateNull_usesToday() {
        LocalDate today = LocalDate.now();
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc()).thenReturn(List.of());
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability("MORNING", null);

        assertThat(result.getDate()).isEqualTo(today.toString());
        verify(seatBookingRepository).findBookedSeatIds("MORNING", today);
    }

    // ── getAvailability — counts and grouping ─────────────────────────────────

    @Test
    void getAvailability_bookedSeatsCountedCorrectly() {
        Seat a1 = seat("A1", "A", 1);
        Seat a2 = seat("A2", "A", 2);
        Seat b1 = seat("B1", "B", 1);
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc())
                .thenReturn(List.of(a1, a2, b1));
        when(seatBookingRepository.findBookedSeatIds(any(), any()))
                .thenReturn(List.of(a1.getId(), b1.getId()));

        SeatAvailabilityDto result = seatService.getAvailability("MORNING", "2025-01-01");

        assertThat(result.getTotalSeats()).isEqualTo(3);
        assertThat(result.getBookedSeats()).isEqualTo(2);
        assertThat(result.getAvailableSeats()).isEqualTo(1);
    }

    @Test
    void getAvailability_seatsGroupedByRow() {
        Seat a1 = seat("A1", "A", 1);
        Seat a2 = seat("A2", "A", 2);
        Seat b1 = seat("B1", "B", 1);
        when(seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc())
                .thenReturn(List.of(a1, a2, b1));
        when(seatBookingRepository.findBookedSeatIds(any(), any())).thenReturn(List.of());

        SeatAvailabilityDto result = seatService.getAvailability("MORNING", "2025-01-01");

        assertThat(result.getSeatsByRow()).containsOnlyKeys("A", "B");
        assertThat(result.getSeatsByRow().get("A")).hasSize(2);
        assertThat(result.getSeatsByRow().get("B")).hasSize(1);
    }

    // ── bookSeat — validation ─────────────────────────────────────────────────

    @Test
    void bookSeat_seatNotFound_throwsResourceNotFoundException() {
        when(seatRepository.findBySeatNumber("X99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.bookSeat(UUID.randomUUID().toString(), req("X99", "MORNING", "2025-01-01", "2025-01-31")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("X99");
    }

    @Test
    void bookSeat_seatInactive_throwsIllegalArgumentException() {
        Seat inactive = Seat.builder().id(UUID.randomUUID()).seatNumber("A1")
                .rowLabel("A").seatIndex(1).isActive(false).build();
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> seatService.bookSeat(UUID.randomUUID().toString(), req("A1", "MORNING", "2025-01-01", "2025-01-31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void bookSeat_conflict_throwsSeatAlreadyBookedException() {
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        when(seatBookingRepository
                .existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
                        any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> seatService.bookSeat(UUID.randomUUID().toString(), req("A1", "MORNING", "2025-01-01", "2025-01-31")))
                .isInstanceOf(SeatAlreadyBookedException.class)
                .hasMessageContaining("A1");
    }

    // ── bookSeat — happy path ─────────────────────────────────────────────────

    @Test
    void bookSeat_success_savesBookingAndReturnsDto() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();

        SeatBooking saved = savedBooking(s, "MORNING", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31), userId);
        when(seatBookingRepository.save(any())).thenReturn(saved);

        SeatBookingDto result = seatService.bookSeat(userId, req("A1", "MORNING", "2025-01-01", "2025-01-31"));

        assertThat(result.getSeatNumber()).isEqualTo("A1");
        assertThat(result.getShift()).isEqualTo("MORNING");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(seatBookingRepository).save(any(SeatBooking.class));
    }

    @Test
    void bookSeat_persistedBookingHasCorrectFields() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();

        SeatBooking saved = savedBooking(s, "MORNING", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31), userId);
        when(seatBookingRepository.save(any())).thenReturn(saved);

        seatService.bookSeat(userId, req("A1", "MORNING", "2025-01-01", "2025-01-31"));

        ArgumentCaptor<SeatBooking> captor = ArgumentCaptor.forClass(SeatBooking.class);
        verify(seatBookingRepository).save(captor.capture());
        SeatBooking persisted = captor.getValue();

        assertThat(persisted.getUserId()).isEqualTo(UUID.fromString(userId));
        assertThat(persisted.getShift()).isEqualTo("MORNING");
        assertThat(persisted.getBookingDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(persisted.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(persisted.getStatus()).isEqualTo(SeatBooking.Status.ACTIVE);
    }

    @Test
    void bookSeat_unknownShift_resolvesToFullDay() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();

        SeatBooking saved = savedBooking(s, "FULL_DAY", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31), userId);
        when(seatBookingRepository.save(any())).thenReturn(saved);

        seatService.bookSeat(userId, req("A1", "WEEKEND", "2025-01-01", "2025-01-31"));

        ArgumentCaptor<SeatBooking> captor = ArgumentCaptor.forClass(SeatBooking.class);
        verify(seatBookingRepository).save(captor.capture());
        assertThat(captor.getValue().getShift()).isEqualTo("FULL_DAY");
    }

    // ── bookSeat — cache invalidation ─────────────────────────────────────────

    @Test
    void bookSeat_invalidatesCacheForBookingDates() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();
        when(seatBookingRepository.save(any())).thenReturn(
                savedBooking(s, "MORNING", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), userId));

        seatService.bookSeat(userId, req("A1", "MORNING", "2025-01-01", "2025-01-01"));

        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-01");
    }

    @Test
    void bookSeat_fullDay_alsoInvalidatesMorningAndEveningCaches() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();
        when(seatBookingRepository.save(any())).thenReturn(
                savedBooking(s, "FULL_DAY", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), userId));

        seatService.bookSeat(userId, req("A1", "FULL_DAY", "2025-01-01", "2025-01-01"));

        verify(redisTemplate).delete("seats:availability:FULL_DAY:2025-01-01");
        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-01");
        verify(redisTemplate).delete("seats:availability:EVENING:2025-01-01");
    }

    @Test
    void bookSeat_multiDay_invalidatesEachDate() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        when(seatRepository.findBySeatNumber("A1")).thenReturn(Optional.of(s));
        noConflict();
        when(seatBookingRepository.save(any())).thenReturn(
                savedBooking(s, "MORNING", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), userId));

        seatService.bookSeat(userId, req("A1", "MORNING", "2025-01-01", "2025-01-03"));

        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-01");
        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-02");
        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-03");
    }

    // ── releaseSeat ───────────────────────────────────────────────────────────

    @Test
    void releaseSeat_membershipNotFound_noOp() {
        when(seatBookingRepository.findByMembershipId(any())).thenReturn(Optional.empty());

        assertThatCode(() -> seatService.releaseSeat(UUID.randomUUID().toString()))
                .doesNotThrowAnyException();
        verify(seatBookingRepository, never()).save(any());
    }

    @Test
    void releaseSeat_found_setsReleasedStatus() {
        UUID membershipId = UUID.randomUUID();
        Seat s = seat("A1", "A", 1);
        SeatBooking booking = SeatBooking.builder()
                .id(UUID.randomUUID()).seat(s).userId(UUID.randomUUID())
                .membershipId(membershipId).shift("MORNING")
                .bookingDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 1, 1))
                .status(SeatBooking.Status.ACTIVE).build();
        when(seatBookingRepository.findByMembershipId(membershipId)).thenReturn(Optional.of(booking));
        when(seatBookingRepository.save(any())).thenReturn(booking);

        seatService.releaseSeat(membershipId.toString());

        ArgumentCaptor<SeatBooking> captor = ArgumentCaptor.forClass(SeatBooking.class);
        verify(seatBookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SeatBooking.Status.RELEASED);
    }

    @Test
    void releaseSeat_found_invalidatesCache() {
        UUID membershipId = UUID.randomUUID();
        Seat s = seat("A1", "A", 1);
        SeatBooking booking = SeatBooking.builder()
                .id(UUID.randomUUID()).seat(s).userId(UUID.randomUUID())
                .membershipId(membershipId).shift("MORNING")
                .bookingDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 1, 1))
                .status(SeatBooking.Status.ACTIVE).build();
        when(seatBookingRepository.findByMembershipId(membershipId)).thenReturn(Optional.of(booking));
        when(seatBookingRepository.save(any())).thenReturn(booking);

        seatService.releaseSeat(membershipId.toString());

        verify(redisTemplate).delete("seats:availability:MORNING:2025-01-01");
    }

    // ── getMyBookings ─────────────────────────────────────────────────────────

    @Test
    void getMyBookings_returnsActiveBookings() {
        String userId = UUID.randomUUID().toString();
        Seat s = seat("A1", "A", 1);
        SeatBooking b = savedBooking(s, "MORNING", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31), userId);
        when(seatBookingRepository.findByUserIdAndStatus(UUID.fromString(userId), SeatBooking.Status.ACTIVE))
                .thenReturn(List.of(b));

        List<SeatBookingDto> result = seatService.getMyBookings(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeatNumber()).isEqualTo("A1");
    }

    @Test
    void getMyBookings_empty_returnsEmptyList() {
        String userId = UUID.randomUUID().toString();
        when(seatBookingRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());

        assertThat(seatService.getMyBookings(userId)).isEmpty();
    }

    // ── getBookingsForShiftAndDate ─────────────────────────────────────────────

    @Test
    void getBookingsForShiftAndDate_withDate_passesCorrectParams() {
        when(seatBookingRepository.findActiveBookingsForShiftAndDate(any(), any()))
                .thenReturn(List.of());

        seatService.getBookingsForShiftAndDate("EVENING", "2025-01-15");

        verify(seatBookingRepository)
                .findActiveBookingsForShiftAndDate("EVENING", LocalDate.of(2025, 1, 15));
    }

    @Test
    void getBookingsForShiftAndDate_nullDate_usesToday() {
        when(seatBookingRepository.findActiveBookingsForShiftAndDate(any(), any()))
                .thenReturn(List.of());

        seatService.getBookingsForShiftAndDate("MORNING", null);

        verify(seatBookingRepository)
                .findActiveBookingsForShiftAndDate("MORNING", LocalDate.now());
    }
}
