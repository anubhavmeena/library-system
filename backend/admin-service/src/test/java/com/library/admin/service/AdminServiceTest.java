package com.library.admin.service;

import com.library.admin.dto.*;
import com.library.admin.entity.*;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository       userRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock PaymentRepository    paymentRepository;
    @Mock PlanRepository       planRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks AdminService adminService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .name("Alice")
                .mobile("9876543210")
                .email("alice@test.com")
                .isActive(true)
                .role(User.Role.STUDENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Membership buildActiveMembership(UUID userId, LocalDate endDate) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planId(UUID.randomUUID())
                .seatNumber("B5")
                .shift("MORNING")
                .startDate(LocalDate.now().minusDays(25))
                .endDate(endDate)
                .status(Membership.Status.ACTIVE)
                .build();
    }

    // ================================================================
    //  getDashboardStats
    // ================================================================

    @Test
    void getDashboardStats_happyPath() {
        when(userRepository.countAllStudents()).thenReturn(50L);
        when(userRepository.countActiveStudents()).thenReturn(45L);
        when(membershipRepository.countActiveMemberships()).thenReturn(40L);
        when(membershipRepository.countExpiredMemberships()).thenReturn(10L);
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(
                buildActiveMembership(UUID.randomUUID(), LocalDate.now().plusDays(3))));
        when(paymentRepository.sumRevenueForPeriod(any(), any()))
                .thenReturn(new BigDecimal("1200.00"), new BigDecimal("35000.00"));
        when(paymentRepository.countSuccessfulPayments(any(), any())).thenReturn(58L);

        DashboardDto dto = adminService.getDashboardStats();

        assertThat(dto.getTotalStudents()).isEqualTo(50L);
        assertThat(dto.getActiveStudents()).isEqualTo(45L);
        assertThat(dto.getActiveMemberships()).isEqualTo(40L);
        assertThat(dto.getExpiredMemberships()).isEqualTo(10L);
        assertThat(dto.getExpiringThisWeek()).isEqualTo(1L);
        assertThat(dto.getTotalSeats()).isEqualTo(110L);
        assertThat(dto.getOccupiedSeats()).isEqualTo(40L);
        assertThat(dto.getAvailableSeats()).isEqualTo(70L);
        assertThat(dto.getRevenueToday()).isEqualByComparingTo("1200.00");
        assertThat(dto.getRevenueThisMonth()).isEqualByComparingTo("35000.00");
        assertThat(dto.getPaymentsThisMonth()).isEqualTo(58L);
    }

    @Test
    void getDashboardStats_nullRevenue_defaultsToZero() {
        when(userRepository.countAllStudents()).thenReturn(0L);
        when(userRepository.countActiveStudents()).thenReturn(0L);
        when(membershipRepository.countActiveMemberships()).thenReturn(0L);
        when(membershipRepository.countExpiredMemberships()).thenReturn(0L);
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(paymentRepository.sumRevenueForPeriod(any(), any())).thenReturn(null);
        when(paymentRepository.countSuccessfulPayments(any(), any())).thenReturn(0L);

        DashboardDto dto = adminService.getDashboardStats();

        assertThat(dto.getRevenueToday()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getDashboardStats_activeMembershipsExceedsSeats_availableIsZero() {
        when(membershipRepository.countActiveMemberships()).thenReturn(120L); // > 110
        when(userRepository.countAllStudents()).thenReturn(0L);
        when(userRepository.countActiveStudents()).thenReturn(0L);
        when(membershipRepository.countExpiredMemberships()).thenReturn(0L);
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(paymentRepository.sumRevenueForPeriod(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countSuccessfulPayments(any(), any())).thenReturn(0L);

        DashboardDto dto = adminService.getDashboardStats();

        assertThat(dto.getAvailableSeats()).isZero();
    }

    @Test
    void getDashboardStats_noExpiringMemberships_expiringThisWeekIsZero() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.countAllStudents()).thenReturn(0L);
        when(userRepository.countActiveStudents()).thenReturn(0L);
        when(membershipRepository.countActiveMemberships()).thenReturn(0L);
        when(membershipRepository.countExpiredMemberships()).thenReturn(0L);
        when(paymentRepository.sumRevenueForPeriod(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countSuccessfulPayments(any(), any())).thenReturn(0L);

        DashboardDto dto = adminService.getDashboardStats();

        assertThat(dto.getExpiringThisWeek()).isZero();
    }

    // ================================================================
    //  getAllStudents
    // ================================================================

    @Test
    void getAllStudents_returnsStudentsWithActiveMemberships() {
        UUID uid = UUID.randomUUID();
        User user = buildUser(uid);
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(10));

        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findStudentsByStatus(isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.of(mem));

        List<StudentDto> result = adminService.getAllStudents(0, 20, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMembershipId()).isEqualTo(mem.getId().toString());
    }

    @Test
    void getAllStudents_studentWithNoMembership_hasMembershipFieldsNull() {
        UUID uid = UUID.randomUUID();
        Page<User> page = new PageImpl<>(List.of(buildUser(uid)));
        when(userRepository.findStudentsByStatus(any(), any(), any(Pageable.class))).thenReturn(page);
        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());

        List<StudentDto> result = adminService.getAllStudents(0, 20, null, null);

        assertThat(result.get(0).getMembershipId()).isNull();
        assertThat(result.get(0).getDaysRemaining()).isZero();
    }

    @Test
    void getAllStudents_forwardsStatusParamToRepo() {
        when(userRepository.findStudentsByStatus(eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.getAllStudents(0, 10, "ACTIVE", null);

        verify(userRepository).findStudentsByStatus(eq("ACTIVE"), isNull(), any(Pageable.class));
    }

    @Test
    void getAllStudents_emptyPage_returnsEmptyList() {
        when(userRepository.findStudentsByStatus(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<StudentDto> result = adminService.getAllStudents(0, 20, null, null);

        assertThat(result).isEmpty();
    }

    // ================================================================
    //  getStudentDetails
    // ================================================================

    @Test
    void getStudentDetails_foundWithMembership() {
        UUID uid = UUID.randomUUID();
        User user = buildUser(uid);
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));

        when(userRepository.findById(uid)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.of(mem));

        StudentDto dto = adminService.getStudentDetails(uid.toString());

        assertThat(dto.getId()).isEqualTo(uid.toString());
        assertThat(dto.getMembershipId()).isEqualTo(mem.getId().toString());
    }

    @Test
    void getStudentDetails_foundWithNoMembership() {
        UUID uid = UUID.randomUUID();
        when(userRepository.findById(uid)).thenReturn(Optional.of(buildUser(uid)));
        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());

        StudentDto dto = adminService.getStudentDetails(uid.toString());

        assertThat(dto.getMembershipId()).isNull();
    }

    @Test
    void getStudentDetails_notFound_throwsResourceNotFoundException() {
        UUID uid = UUID.randomUUID();
        when(userRepository.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getStudentDetails(uid.toString()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(uid.toString());
    }

    // ================================================================
    //  getSeatMap
    // ================================================================

    @Test
    void getSeatMap_nullDate_defaultsToToday() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getDate()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void getSeatMap_specificDate_usedAsIs() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", "2025-06-01");

        assertThat(dto.getDate()).isEqualTo("2025-06-01");
    }

    @Test
    void getSeatMap_totalSeatsAlways110() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getTotalSeats()).isEqualTo(110);
    }

    @Test
    void getSeatMap_rowStructure_correctSeatCounts() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getSeatsByRow()).containsKeys("A", "B", "C", "D");
        assertThat(dto.getSeatsByRow().get("A")).hasSize(28);
        assertThat(dto.getSeatsByRow().get("B")).hasSize(28);
        assertThat(dto.getSeatsByRow().get("C")).hasSize(28);
        assertThat(dto.getSeatsByRow().get("D")).hasSize(26);
    }

    @Test
    void getSeatMap_occupiedSeatPlusAvailableEqualsTotal() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        User user = buildUser(uid);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getOccupiedSeats() + dto.getAvailableSeats()).isEqualTo(110);
        assertThat(dto.getOccupiedSeats()).isEqualTo(1);
    }

    @Test
    void getSeatMap_morningShift_includesMorningBooking() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        mem.setShift("MORNING");
        User user = buildUser(uid);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        SeatMapDto dto = adminService.getSeatMap("MORNING", null);

        assertThat(dto.getOccupiedSeats()).isEqualTo(1);
    }

    @Test
    void getSeatMap_morningShift_includesFullDayBooking() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        mem.setShift("FULL_DAY"); // FULL_DAY occupies both sub-shifts
        User user = buildUser(uid);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        SeatMapDto dto = adminService.getSeatMap("MORNING", null);

        assertThat(dto.getOccupiedSeats()).isEqualTo(1);
    }

    @Test
    void getSeatMap_morningShift_excludesEveningBooking() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        mem.setShift("EVENING");

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("MORNING", null);

        assertThat(dto.getOccupiedSeats()).isZero();
    }

    @Test
    void getSeatMap_membership_startDateAfterQueryDate_excluded() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        mem.setStartDate(LocalDate.now().plusDays(1)); // starts tomorrow

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getOccupiedSeats()).isZero();
    }

    @Test
    void getSeatMap_membership_endDateBeforeQueryDate_excluded() {
        UUID uid = UUID.randomUUID();
        // We pass a future date to query but set the membership as already ended
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");
        // Query for a date after end
        String futureDate = LocalDate.now().plusDays(10).toString();

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", futureDate);

        assertThat(dto.getOccupiedSeats()).isZero();
    }

    @Test
    void getSeatMap_twoBookingsForSameSeat_firstWins() {
        UUID uid1 = UUID.randomUUID();
        UUID uid2 = UUID.randomUUID();

        Membership m1 = buildActiveMembership(uid1, LocalDate.now().plusDays(5));
        m1.setSeatNumber("A1");
        Membership m2 = buildActiveMembership(uid2, LocalDate.now().plusDays(5));
        m2.setSeatNumber("A1"); // same seat

        User user1 = buildUser(uid1);
        User user2 = buildUser(uid2);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user1, user2));

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        // Only 1 seat is marked occupied despite 2 bookings
        assertThat(dto.getOccupiedSeats()).isEqualTo(1);
    }

    @Test
    void getSeatMap_userNotInMap_seatShownAsOccupiedWithUnknownName() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        mem.setSeatNumber("A1");

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of()); // user not found

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        SeatMapDto.SeatInfoDto seatA1 = dto.getSeatsByRow().get("A").stream()
                .filter(s -> "A1".equals(s.getSeatNumber()))
                .findFirst().orElseThrow();

        assertThat(seatA1.getIsOccupied()).isTrue();
        assertThat(seatA1.getStudentName()).isEqualTo("Unknown");
        assertThat(seatA1.getStudentMobile()).isNull();
    }

    @Test
    void getSeatMap_noBookings_allSeatsAvailable() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        SeatMapDto dto = adminService.getSeatMap("FULL_DAY", null);

        assertThat(dto.getOccupiedSeats()).isZero();
        assertThat(dto.getAvailableSeats()).isEqualTo(110);
        dto.getSeatsByRow().values().forEach(seats ->
                seats.forEach(s -> assertThat(s.getIsOccupied()).isFalse()));
    }

    // ================================================================
    //  getExpiringMemberships
    // ================================================================

    @Test
    void getExpiringMemberships_returnsSortedByMembershipEndAsc() {
        UUID uid1 = UUID.randomUUID();
        UUID uid2 = UUID.randomUUID();
        Membership m1 = buildActiveMembership(uid1, LocalDate.now().plusDays(6));
        Membership m2 = buildActiveMembership(uid2, LocalDate.now().plusDays(2));

        when(membershipRepository.findMembershipsExpiringBefore(any()))
                .thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(buildUser(uid1), buildUser(uid2)));

        List<StudentDto> result = adminService.getExpiringMemberships(7);

        assertThat(result.get(0).getMembershipEnd())
                .isLessThan(result.get(1).getMembershipEnd());
    }

    @Test
    void getExpiringMemberships_userNotInMap_skipped() {
        UUID uid = UUID.randomUUID();
        when(membershipRepository.findMembershipsExpiringBefore(any()))
                .thenReturn(List.of(buildActiveMembership(uid, LocalDate.now().plusDays(3))));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of()); // user missing

        List<StudentDto> result = adminService.getExpiringMemberships(7);

        assertThat(result).isEmpty();
    }

    @Test
    void getExpiringMemberships_empty_returnsEmptyList() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        List<StudentDto> result = adminService.getExpiringMemberships(7);

        assertThat(result).isEmpty();
    }

    // ================================================================
    //  sendBulkReminders
    // ================================================================

    @Test
    void sendBulkReminders_nullUserIds_sendsToAllExpiring() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        User user = buildUser(uid);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        int count = adminService.sendBulkReminders(null);

        assertThat(count).isEqualTo(1);
        verify(kafkaTemplate).send(eq("renewal-reminder"), eq(uid.toString()), any(RenewalReminderEvent.class));
    }

    @Test
    void sendBulkReminders_emptyList_sendsToAllExpiring() {
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        int count = adminService.sendBulkReminders(List.of());

        assertThat(count).isZero();
        verify(membershipRepository).findMembershipsExpiringBefore(any()); // global fetch used
    }

    @Test
    void sendBulkReminders_specificUserIds_onlySendsToThose() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(3));
        User user = buildUser(uid);

        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        int count = adminService.sendBulkReminders(List.of(uid.toString()));

        assertThat(count).isEqualTo(1);
        verify(membershipRepository, never()).findMembershipsExpiringBefore(any());
    }

    @Test
    void sendBulkReminders_membershipNotFoundForUserId_skipped() {
        UUID uid = UUID.randomUUID();
        when(membershipRepository.findByUserIdAndStatus(uid, Membership.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        int count = adminService.sendBulkReminders(List.of(uid.toString()));

        assertThat(count).isZero();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendBulkReminders_userNotInMap_skipped() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(3));

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of()); // user missing

        int count = adminService.sendBulkReminders(null);

        assertThat(count).isZero();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendBulkReminders_kafkaEventFieldsAreCorrect() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(4));
        mem.setSeatNumber("C12");
        User user = buildUser(uid);
        user.setName("Bob");
        user.setMobile("1234567890");
        user.setEmail("bob@test.com");

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        adminService.sendBulkReminders(null);

        ArgumentCaptor<RenewalReminderEvent> captor = ArgumentCaptor.forClass(RenewalReminderEvent.class);
        verify(kafkaTemplate).send(eq("renewal-reminder"), eq(uid.toString()), captor.capture());

        RenewalReminderEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(uid.toString());
        assertThat(event.getMembershipId()).isEqualTo(mem.getId().toString());
        assertThat(event.getUserName()).isEqualTo("Bob");
        assertThat(event.getUserMobile()).isEqualTo("1234567890");
        assertThat(event.getUserEmail()).isEqualTo("bob@test.com");
        assertThat(event.getSeatNumber()).isEqualTo("C12");
        assertThat(event.getExpiryDate()).isEqualTo(mem.getEndDate().toString());
        assertThat(event.getDaysRemaining()).isEqualTo(4);
        assertThat(event.getEventType()).isEqualTo("RENEWAL_REMINDER");
    }

    @Test
    void sendBulkReminders_daysRemainingInPast_clampedToZero() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().minusDays(2)); // already expired
        User user = buildUser(uid);

        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user));

        adminService.sendBulkReminders(null);

        ArgumentCaptor<RenewalReminderEvent> captor = ArgumentCaptor.forClass(RenewalReminderEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());
        assertThat(captor.getValue().getDaysRemaining()).isZero();
    }

    @Test
    void sendBulkReminders_doesNotSetReminderSentFlag() {
        UUID uid = UUID.randomUUID();
        Membership mem = buildActiveMembership(uid, LocalDate.now().plusDays(5));
        when(membershipRepository.findMembershipsExpiringBefore(any())).thenReturn(List.of(mem));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(buildUser(uid)));

        adminService.sendBulkReminders(null);

        verify(membershipRepository, never()).save(any());
    }

    // ================================================================
    //  getRevenueReport
    // ================================================================

    @Test
    void getRevenueReport_singleDay_includesDayWithTransactions() {
        String date = "2025-03-10";
        when(paymentRepository.sumRevenueForPeriod(any(), any()))
                .thenReturn(new BigDecimal("500.00"));
        when(paymentRepository.countSuccessfulPayments(any(), any()))
                .thenReturn(3L);

        RevenueReportDto dto = adminService.getRevenueReport(date, date);

        assertThat(dto.getFromDate()).isEqualTo(date);
        assertThat(dto.getToDate()).isEqualTo(date);
        assertThat(dto.getTotalRevenue()).isEqualByComparingTo("500.00");
        assertThat(dto.getTotalTransactions()).isEqualTo(3L);
        assertThat(dto.getDailyBreakdown()).hasSize(1);
        assertThat(dto.getDailyBreakdown().get(0).getDate()).isEqualTo(date);
    }

    @Test
    void getRevenueReport_daysWithNoTransactions_excludedFromBreakdown() {
        // 3-day period: day2 has 0 transactions
        when(paymentRepository.sumRevenueForPeriod(any(), any()))
                .thenReturn(new BigDecimal("300.00"), // total
                        new BigDecimal("100.00"),     // day1
                        BigDecimal.ZERO,              // day2 — count=0 → excluded
                        new BigDecimal("200.00"));    // day3
        when(paymentRepository.countSuccessfulPayments(any(), any()))
                .thenReturn(2L, // total
                        1L,    // day1
                        0L,    // day2 — excluded
                        1L);   // day3

        RevenueReportDto dto = adminService.getRevenueReport("2025-03-10", "2025-03-12");

        assertThat(dto.getDailyBreakdown()).hasSize(2); // day2 skipped
    }

    @Test
    void getRevenueReport_nullRevenue_defaultsToZero() {
        when(paymentRepository.sumRevenueForPeriod(any(), any())).thenReturn(null);
        when(paymentRepository.countSuccessfulPayments(any(), any())).thenReturn(0L);

        RevenueReportDto dto = adminService.getRevenueReport("2025-03-10", "2025-03-10");

        assertThat(dto.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenueReport_multiDayRange_loopsCorrectly() {
        when(paymentRepository.sumRevenueForPeriod(any(), any()))
                .thenReturn(new BigDecimal("1000.00"), // total call
                        new BigDecimal("400.00"),      // day 1
                        new BigDecimal("600.00"));     // day 2
        when(paymentRepository.countSuccessfulPayments(any(), any()))
                .thenReturn(4L, 2L, 2L);

        RevenueReportDto dto = adminService.getRevenueReport("2025-03-10", "2025-03-11");

        assertThat(dto.getDailyBreakdown()).hasSize(2);
    }

    // ================================================================
    //  updateStudentStatus
    // ================================================================

    @Test
    void updateStudentStatus_activeToInactive() {
        UUID uid = UUID.randomUUID();
        User user = buildUser(uid);
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updateStudentStatus(uid.toString(), false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    @Test
    void updateStudentStatus_inactiveToActive() {
        UUID uid = UUID.randomUUID();
        User user = buildUser(uid);
        user.setIsActive(false);
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updateStudentStatus(uid.toString(), true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void updateStudentStatus_notFound_throwsResourceNotFoundException() {
        UUID uid = UUID.randomUUID();
        when(userRepository.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateStudentStatus(uid.toString(), true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(uid.toString());

        verify(userRepository, never()).save(any());
    }
}
