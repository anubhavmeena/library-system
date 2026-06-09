package com.library.admin.service;

import com.library.admin.dto.*;
import com.library.admin.entity.*;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository        userRepository;
    private final MembershipRepository  membershipRepository;
    private final PaymentRepository     paymentRepository;
    private final PlanRepository        planRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    public DashboardDto getDashboardStats() {
        long totalStudents  = userRepository.countAllStudents();
        long activeStudents = userRepository.countActiveStudents();
        long activeMem      = membershipRepository.countActiveMemberships();
        long expiredMem     = membershipRepository.countExpiredMemberships();
        long expiringWeek   = membershipRepository
                .findMembershipsExpiringBefore(LocalDate.now().plusDays(7)).size();

        long totalSeats     = 110L;
        long occupiedSeats  = activeMem;
        long availableSeats = Math.max(0, totalSeats - occupiedSeats);

        LocalDateTime todayStart  = LocalDate.now().atStartOfDay();
        BigDecimal revenueToday   = paymentRepository
                .sumRevenueForPeriod(todayStart, LocalDateTime.now());

        LocalDateTime monthStart  = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal revenueMonth   = paymentRepository
                .sumRevenueForPeriod(monthStart, LocalDateTime.now());
        long paymentsMonth        = paymentRepository
                .countSuccessfulPayments(monthStart, LocalDateTime.now());

        return DashboardDto.builder()
                .totalStudents(totalStudents)
                .activeStudents(activeStudents)
                .activeMemberships(activeMem)
                .expiredMemberships(expiredMem)
                .expiringThisWeek(expiringWeek)
                .totalSeats(totalSeats)
                .occupiedSeats(occupiedSeats)
                .availableSeats(availableSeats)
                .revenueToday(revenueToday   != null ? revenueToday  : BigDecimal.ZERO)
                .revenueThisMonth(revenueMonth != null ? revenueMonth : BigDecimal.ZERO)
                .paymentsThisMonth(paymentsMonth)
                .build();
    }

    // ── Students ──────────────────────────────────────────────────────────────

    public List<StudentDto> getAllStudents(int page, int size, String status) {
        List<User> users = userRepository
                .findStudentsByStatus(status, PageRequest.of(page, size))
                .getContent();

        return users.stream().map(user -> {
            Membership mem = membershipRepository
                    .findByUserIdAndStatus(user.getId(), Membership.Status.ACTIVE)
                    .orElse(null);
            return StudentDto.fromEntities(user, mem);
        }).collect(Collectors.toList());
    }

    public StudentDto getStudentDetails(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + userId));

        Membership mem = membershipRepository
                .findByUserIdAndStatus(user.getId(), Membership.Status.ACTIVE)
                .orElse(null);

        return StudentDto.fromEntities(user, mem);
    }

    // ── Seat Map ──────────────────────────────────────────────────────────────

    public SeatMapDto getSeatMap(String shift, String dateStr) {
        LocalDate date = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr)
                : LocalDate.now();

        // Fetch all ACTIVE memberships with a seat assigned, filtered by shift + date
        List<Membership> active = membershipRepository
                .findMembershipsExpiringBefore(LocalDate.now().plusYears(1))
                .stream()
                .filter(m -> m.getStatus() == Membership.Status.ACTIVE
                        && m.getSeatNumber() != null)
                .filter(m -> !m.getStartDate().isAfter(date)
                        && !m.getEndDate().isBefore(date))
                .filter(m -> "FULL_DAY".equalsIgnoreCase(shift)
                        || shift.equalsIgnoreCase(m.getShift())
                        || "FULL_DAY".equalsIgnoreCase(m.getShift()))
                .collect(Collectors.toList());

        // Build seat-number → membership map (first booking wins on conflict)
        Map<String, Membership> seatMap = active.stream()
                .collect(Collectors.toMap(
                        Membership::getSeatNumber, m -> m, (a, b) -> a));

        // Bulk-load users for the booked seats
        Set<UUID> userIds = active.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Build row-ordered seat grid: A(28), B(28), C(28), D(26)
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        rowCounts.put("A", 28);
        rowCounts.put("B", 28);
        rowCounts.put("C", 28);
        rowCounts.put("D", 26);

        Map<String, List<SeatMapDto.SeatInfoDto>> seatsByRow = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : rowCounts.entrySet()) {
            String row  = entry.getKey();
            int    count = entry.getValue();
            List<SeatMapDto.SeatInfoDto> rowSeats = new ArrayList<>();

            for (int i = 1; i <= count; i++) {
                String     sn  = row + i;
                Membership mem = seatMap.get(sn);

                if (mem != null) {
                    User student = userMap.get(mem.getUserId());
                    rowSeats.add(SeatMapDto.SeatInfoDto.builder()
                            .seatNumber(sn)
                            .isOccupied(true)
                            .studentName(student != null ? student.getName() : "Unknown")
                            .studentMobile(student != null ? student.getMobile() : null)
                            .shift(mem.getShift())
                            .membershipEnd(mem.getEndDate().toString())
                            .build());
                } else {
                    rowSeats.add(SeatMapDto.SeatInfoDto.builder()
                            .seatNumber(sn)
                            .isOccupied(false)
                            .build());
                }
            }
            seatsByRow.put(row, rowSeats);
        }

        return SeatMapDto.builder()
                .shift(shift)
                .date(date.toString())
                .totalSeats(110)
                .occupiedSeats(seatMap.size())
                .availableSeats(110 - seatMap.size())
                .seatsByRow(seatsByRow)
                .build();
    }

    // ── Expiring Memberships ──────────────────────────────────────────────────

    public List<StudentDto> getExpiringMemberships(int withinDays) {
        List<Membership> expiring = membershipRepository
                .findMembershipsExpiringBefore(LocalDate.now().plusDays(withinDays));

        Set<UUID> userIds = expiring.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Keep only first membership per user (should be at most one ACTIVE)
        Map<UUID, Membership> memMap = expiring.stream()
                .collect(Collectors.toMap(
                        Membership::getUserId, m -> m, (a, b) -> a));

        return userIds.stream()
                .filter(userMap::containsKey)
                .map(uid -> StudentDto.fromEntities(userMap.get(uid), memMap.get(uid)))
                .sorted(Comparator.comparing(
                        StudentDto::getMembershipEnd,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // ── Bulk Reminders ────────────────────────────────────────────────────────

    @Transactional
    public int sendBulkReminders(List<String> userIds) {
        List<Membership> targets;

        if (userIds == null || userIds.isEmpty()) {
            // No specific users → send to all expiring within 7 days
            targets = membershipRepository
                    .findMembershipsExpiringBefore(LocalDate.now().plusDays(7));
        } else {
            // Targeted send — resolve membership for each supplied userId
            targets = userIds.stream()
                    .map(id -> membershipRepository
                            .findByUserIdAndStatus(
                                    UUID.fromString(id), Membership.Status.ACTIVE)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        Set<UUID> targetIds = targets.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        int sent = 0;
        for (Membership mem : targets) {
            User user = userMap.get(mem.getUserId());
            if (user == null) continue;

            int days = (int) ChronoUnit.DAYS
                    .between(LocalDate.now(), mem.getEndDate());

            RenewalReminderEvent event = RenewalReminderEvent.builder()
                    .userId(mem.getUserId().toString())
                    .membershipId(mem.getId().toString())
                    .userName(user.getName())
                    .userMobile(user.getMobile())
                    .userEmail(user.getEmail())
                    .seatNumber(mem.getSeatNumber())
                    .expiryDate(mem.getEndDate().toString())
                    .daysRemaining(Math.max(0, days))
                    .eventType("RENEWAL_REMINDER")
                    .build();

            kafkaTemplate.send("renewal-reminder",
                    mem.getUserId().toString(), event);
            sent++;

            log.info("Renewal reminder queued for user '{}' ({} days left)",
                    user.getName(), days);
        }

        log.info("sendBulkReminders: {} reminders published to Kafka", sent);
        return sent;
    }

    // ── Revenue Report ────────────────────────────────────────────────────────

    public RevenueReportDto getRevenueReport(String fromStr, String toStr) {
        LocalDateTime from = LocalDate.parse(fromStr).atStartOfDay();
        LocalDateTime to   = LocalDate.parse(toStr).atTime(23, 59, 59);

        BigDecimal total = paymentRepository.sumRevenueForPeriod(from, to);
        long count       = paymentRepository.countSuccessfulPayments(from, to);

        // Build daily breakdown (only days with transactions)
        List<RevenueReportDto.DailyRevenueDto> daily = new ArrayList<>();
        LocalDate cursor = LocalDate.parse(fromStr);
        LocalDate end    = LocalDate.parse(toStr);

        while (!cursor.isAfter(end)) {
            LocalDateTime dayStart = cursor.atStartOfDay();
            LocalDateTime dayEnd   = cursor.atTime(23, 59, 59);

            BigDecimal dayAmt   = paymentRepository.sumRevenueForPeriod(dayStart, dayEnd);
            long       dayCount = paymentRepository.countSuccessfulPayments(dayStart, dayEnd);

            if (dayCount > 0) {
                daily.add(RevenueReportDto.DailyRevenueDto.builder()
                        .date(cursor.toString())
                        .amount(dayAmt != null ? dayAmt : BigDecimal.ZERO)
                        .count(dayCount)
                        .build());
            }
            cursor = cursor.plusDays(1);
        }

        return RevenueReportDto.builder()
                .fromDate(fromStr)
                .toDate(toStr)
                .totalRevenue(total != null ? total : BigDecimal.ZERO)
                .totalTransactions(count)
                .dailyBreakdown(daily)
                .build();
    }

    // ── Update Student Status ─────────────────────────────────────────────────

    @Transactional
    public void updateStudentStatus(String userId, boolean active) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + userId));
        user.setIsActive(active);
        userRepository.save(user);
        log.info("Student {} set to {}", userId, active ? "ACTIVE" : "INACTIVE");
    }
}