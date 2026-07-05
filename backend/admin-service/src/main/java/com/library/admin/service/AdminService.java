package com.library.admin.service;

import com.library.admin.dto.*;
import com.library.admin.entity.*;
import com.library.admin.event.BroadcastNotificationEvent;
import com.library.admin.event.PaymentReceiptEvent;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserRepository              userRepository;
    private final MembershipRepository        membershipRepository;
    private final PaymentRepository           paymentRepository;
    private final SeatBookingRepository       seatBookingRepository;
    private final SeatRepository              seatRepository;
    private final FeedbackRepository          feedbackRepository;
    private final PlanRepository              planRepository;
    private final BroadcastMessageRepository  broadcastMessageRepository;
    private final VisitorEventRepository       visitorEventRepository;
    private final AppSettingsRepository       appSettingsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EntityManager entityManager;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
        "name",          "u.name",
        "mobile",        "u.mobile",
        "seatNumber",    "m.seat_number",
        "endDate",       "m.end_date",
        "paymentMode",   "p.payment_gateway",
        "createdAt",     "u.created_at",
        "pendingAmount", "p.pending_amount"
    );

    // ── Dashboard ─────────────────────────────────────────────────────────────

    public DashboardDto getDashboardStats() {
        long totalStudents  = userRepository.countAllStudents();
        long activeMem      = membershipRepository.countActiveMemberships();
        long expiredMem     = membershipRepository.countExpiredMemberships();
        long expiringWeek   = membershipRepository
                .findMembershipsExpiringBefore(LocalDate.now().plusDays(7)).size();
        long orphanedSeats  = membershipRepository
                .findActiveMembershipsWithoutSeatBooking().size();

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

        long totalVisitors  = visitorEventRepository.count();
        long visitorsToday  = visitorEventRepository.countByCreatedAtAfter(todayStart);

        return DashboardDto.builder()
                .totalStudents(totalStudents)
                .activeMemberships(activeMem)
                .expiredMemberships(expiredMem)
                .expiringThisWeek(expiringWeek)
                .orphanedSeatMemberships(orphanedSeats)
                .totalSeats(totalSeats)
                .occupiedSeats(occupiedSeats)
                .availableSeats(availableSeats)
                .revenueToday(revenueToday   != null ? revenueToday  : BigDecimal.ZERO)
                .revenueThisMonth(revenueMonth != null ? revenueMonth : BigDecimal.ZERO)
                .paymentsThisMonth(paymentsMonth)
                .totalVisitors(totalVisitors)
                .visitorsToday(visitorsToday)
                .build();
    }

    // ── Students ──────────────────────────────────────────────────────────────

    public StudentListDto getAllStudents(int page, int size, String membershipStatus,
                                         String search, String sortBy, String sortDir) {
        String safeSortCol = SORT_COLUMNS.getOrDefault(sortBy, "u.created_at");
        String safeSortDir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        int graceDays = currentGraceDays();

        // membershipStatus mirrors StudentStatusResolver's 6-value display status
        // (NEW/PAID/PENDING/GRACE/EXPIRED/RELEASED) — the filtering MUST happen here
        // in SQL (not after fetching a page in Java) or pagination breaks. `le` is
        // the student's latest-ever membership row (any status), needed to detect
        // NEW/RELEASED since the `m` join above only ever matches a current
        // (ACTIVE-not-expired or GRACE) row.
        //
        // `m` is restricted to a single row (the student's "current" membership)
        // via a correlated subquery, GRACE-prioritized over ACTIVE regardless of
        // endDate — same ordering as
        // MembershipRepository.findCurrentByUserIdOrderByEndDateDesc. A student
        // can simultaneously have an old unresolved GRACE membership (dues owed)
        // and a newer genuinely-current ACTIVE one (e.g. they self-booked a fresh
        // membership instead of paying off the old one) — an unrestricted
        // `ON m.user_id = u.id AND (...)` join matched BOTH rows, so this student
        // could appear in the PAID tab (via the ACTIVE row satisfying that
        // branch) while the per-row Java enrichment below independently resolved
        // to the GRACE row via findFirstByUserIdCurrentOrderByEndDateDesc,
        // producing a GRACE/EXPIRED status badge on a row filtered into "Paid".
        //
        // `p` is restricted to a single row (the most recent payment) via a
        // correlated subquery, same pattern as `le` below — a membership can have
        // more than one Payment row (e.g. two separate cash installments), and an
        // unrestricted `ON p.membership_id = m.id` join fans out into one result
        // row per payment. With no DISTINCT on the paginated SELECT, that made the
        // same student appear more than once in the list (and could even split
        // one student across both the PAID and PENDING tabs, since each joined
        // row carries a different p.pending_amount). Must match
        // PaymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc, which the
        // per-row Java enrichment below uses to resolve "the" current payment.
        String where = """
                FROM users u
                LEFT JOIN memberships m ON m.id = (
                    SELECT m1.id FROM memberships m1
                    WHERE m1.user_id = u.id
                      AND (m1.status = 'GRACE' OR (m1.status = 'ACTIVE' AND m1.end_date >= CURRENT_DATE))
                    ORDER BY CASE WHEN m1.status = 'GRACE' THEN 0 ELSE 1 END, m1.end_date DESC
                    LIMIT 1
                )
                LEFT JOIN payments p ON p.id = (
                    SELECT p2.id FROM payments p2
                    WHERE p2.membership_id = m.id
                    ORDER BY p2.created_at DESC
                    LIMIT 1
                )
                LEFT JOIN memberships le ON le.id = (
                    SELECT m2.id FROM memberships m2
                    WHERE m2.user_id = u.id
                    ORDER BY m2.end_date DESC
                    LIMIT 1
                )
                WHERE u.role = 'STUDENT'
                  AND (CAST(:membershipStatus AS VARCHAR) IS NULL
                       -- graceDays comes from AppSettings, fetched once above via currentGraceDays()
                       OR (CAST(:membershipStatus AS VARCHAR) = 'GRACE'
                           AND m.status = 'GRACE' AND (CURRENT_DATE - m.end_date) <= %1$d)
                       OR (CAST(:membershipStatus AS VARCHAR) = 'EXPIRED'
                           AND m.status = 'GRACE' AND (CURRENT_DATE - m.end_date) > %1$d)
                       OR (CAST(:membershipStatus AS VARCHAR) = 'PAID'
                           AND m.status = 'ACTIVE' AND (p.pending_amount IS NULL OR p.pending_amount <= 0))
                       OR (CAST(:membershipStatus AS VARCHAR) = 'PENDING'
                           AND m.status = 'ACTIVE' AND p.pending_amount > 0)
                       OR (CAST(:membershipStatus AS VARCHAR) = 'RELEASED'
                           AND m.id IS NULL AND le.status IN ('EXPIRED', 'CANCELLED'))
                       OR (CAST(:membershipStatus AS VARCHAR) = 'NEW'
                           AND m.id IS NULL AND (le.id IS NULL OR le.status NOT IN ('EXPIRED', 'CANCELLED'))))
                  AND (CAST(:search AS VARCHAR) IS NULL
                       OR LOWER(u.name)  LIKE LOWER(CONCAT('%%', CAST(:search AS VARCHAR), '%%'))
                       OR u.mobile       LIKE CONCAT('%%', CAST(:search AS VARCHAR), '%%'))
                """.formatted(graceDays);

        @SuppressWarnings("unchecked")
        List<User> users = entityManager
                .createNativeQuery("SELECT u.* " + where + " ORDER BY " + safeSortCol + " " + safeSortDir, User.class)
                .setParameter("membershipStatus", membershipStatus)
                .setParameter("search", search)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        long total = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(DISTINCT u.id) " + where)
                .setParameter("membershipStatus", membershipStatus)
                .setParameter("search", search)
                .getSingleResult()).longValue();

        List<StudentDto> students = users.stream().map(user -> {
            Membership mem = membershipRepository
                    .findFirstByUserIdCurrentOrderByEndDateDesc(user.getId())
                    .filter(m -> m.getStatus() == Membership.Status.GRACE
                            || !m.getEndDate().isBefore(LocalDate.now()))
                    .orElse(null);
            StudentDto dto = StudentDto.fromEntities(user, mem);
            Payment currentPayment = null;
            Membership latestEver = null;
            if (mem != null) {
                currentPayment = paymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc(mem.getId()).orElse(null);
                if (currentPayment != null) {
                    dto.setPaymentMode("CASH".equalsIgnoreCase(currentPayment.getPaymentGateway()) ? "CASH" : "ONLINE");
                    dto.setPendingAmount(currentPayment.getPendingAmount() != null ? currentPayment.getPendingAmount() : BigDecimal.ZERO);
                }
            } else {
                latestEver = membershipRepository
                        .findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(user.getId(), Membership.Status.PENDING)
                        .orElse(null);
                if (latestEver != null) {
                    dto.setMembershipStatus("EXPIRED");
                }
            }
            dto.setDisplayStatus(StudentStatusResolver.resolve(mem, currentPayment, latestEver, graceDays).name());
            return dto;
        }).collect(Collectors.toList());
        return new StudentListDto(students, total);
    }

    public StudentDto getStudentDetails(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + userId));

        Membership mem = membershipRepository
                .findFirstByUserIdCurrentOrderByEndDateDesc(user.getId())
                .filter(m -> m.getStatus() == Membership.Status.GRACE
                        || !m.getEndDate().isBefore(LocalDate.now()))
                .orElse(null);

        StudentDto dto = StudentDto.fromEntities(user, mem);
        Payment currentPayment = null;
        Membership latestEver = null;
        if (mem != null) {
            planRepository.findById(mem.getPlanId()).ifPresent(plan -> {
                dto.setMembershipPlanId(plan.getId().toString());
                dto.setPlanName(plan.getName());
                dto.setPlanType(plan.getPlanType().name());
            });
            currentPayment = paymentRepository.findFirstByMembershipIdOrderByCreatedAtDesc(mem.getId()).orElse(null);
            if (currentPayment != null) {
                dto.setPaymentMode("CASH".equalsIgnoreCase(currentPayment.getPaymentGateway()) ? "CASH" : "ONLINE");
                dto.setPendingAmount(currentPayment.getPendingAmount() != null ? currentPayment.getPendingAmount() : BigDecimal.ZERO);
            }
        } else {
            latestEver = membershipRepository
                    .findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(user.getId(), Membership.Status.PENDING)
                    .orElse(null);
            if (latestEver != null) {
                dto.setMembershipStatus("EXPIRED");
                dto.setMembershipEnd(latestEver.getEndDate().toString());
            }
        }
        dto.setDisplayStatus(StudentStatusResolver.resolve(mem, currentPayment, latestEver, currentGraceDays()).name());
        return dto;
    }

    // ── Seat Map ──────────────────────────────────────────────────────────────

    public SeatMapDto getSeatMap(String shift, String dateStr) {
        LocalDate date = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr)
                : LocalDate.now();

        // Fetch ACTIVE (date-bound) and GRACE (held indefinitely) memberships with a
        // seat assigned, filtered by shift + date. A GRACE seat has no upper date
        // bound — it stays occupied on the map until an admin releases it. A
        // membership that is still ACTIVE in the DB but whose endDate has already
        // passed as of the real "today" is treated the same way: the seat is
        // still physically held (nobody renewed, nobody released it) even though
        // ExpiryReminderScheduler hasn't run yet today to flip status -> GRACE.
        // See isHeldIndefinitely().
        List<Membership> active = membershipRepository
                .findOccupyingSeatMemberships(LocalDate.now().plusYears(1))
                .stream()
                .filter(m -> isHeldIndefinitely(m)
                        ? !m.getStartDate().isAfter(date)
                        : (!m.getStartDate().isAfter(date) && !m.getEndDate().isBefore(date)))
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

        // Real seat layout, read from the `seats` table — not a hardcoded
        // row-count literal. seat-service and createCashMembership() both
        // validate bookings against this same table, so building the grid
        // from anything else can silently drift out of sync with what's
        // actually bookable (a hardcoded "D has 26 seats" literal here once
        // hid two genuinely-occupied seats — D27/D28 — that existed as active
        // rows in `seats` but fell outside that literal's range).
        List<Seat> allSeats = seatRepository.findByIsActiveTrueOrderByRowLabelAscSeatIndexAsc();

        Set<String> activeSeatNumbers = allSeats.stream().map(Seat::getSeatNumber).collect(Collectors.toSet());
        seatMap.forEach((sn, mem) -> {
            if (!activeSeatNumbers.contains(sn)) {
                log.warn("Membership {} occupies seat '{}', which is not an active row in the seats " +
                        "table — it will not appear on the admin seat map. Check for stale/incorrect seat data.",
                        mem.getId(), sn);
            }
        });

        Map<String, List<SeatMapDto.SeatInfoDto>> seatsByRow = new LinkedHashMap<>();
        int occupiedCount = 0;

        for (Seat seat : allSeats) {
            String sn  = seat.getSeatNumber();
            Membership mem = seatMap.get(sn);
            List<SeatMapDto.SeatInfoDto> rowSeats =
                    seatsByRow.computeIfAbsent(seat.getRowLabel(), k -> new ArrayList<>());

            if (mem != null) {
                occupiedCount++;
                User    student          = userMap.get(mem.getUserId());
                boolean heldIndefinitely = isHeldIndefinitely(mem);
                Integer daysOverdue = heldIndefinitely
                        ? (int) ChronoUnit.DAYS.between(mem.getEndDate(), LocalDate.now())
                        : null;
                rowSeats.add(SeatMapDto.SeatInfoDto.builder()
                        .seatNumber(sn)
                        .isOccupied(true)
                        .studentId(mem.getUserId().toString())
                        .studentName(student != null ? student.getName() : "Unknown")
                        .studentMobile(student != null ? student.getMobile() : null)
                        .studentGender(student != null ? student.getGender() : null)
                        .shift(mem.getShift())
                        .membershipEnd(mem.getEndDate().toString())
                        .membershipId(mem.getId().toString())
                        .membershipStatus(mem.getStatus().name())
                        .daysOverdue(daysOverdue)
                        .build());
            } else {
                rowSeats.add(SeatMapDto.SeatInfoDto.builder()
                        .seatNumber(sn)
                        .isOccupied(false)
                        .build());
            }
        }

        return SeatMapDto.builder()
                .shift(shift)
                .date(date.toString())
                .totalSeats(allSeats.size())
                .occupiedSeats(occupiedCount)
                .availableSeats(allSeats.size() - occupiedCount)
                .seatsByRow(seatsByRow)
                .build();
    }

    // True for a GRACE membership (held indefinitely by definition, until an
    // admin explicitly releases the seat) and also for an ACTIVE membership
    // whose endDate has already passed as of the real current date. The latter
    // covers the daily window between midnight — when "today" rolls past
    // endDate — and ExpiryReminderScheduler's run, during which the DB row
    // hasn't been flipped ACTIVE -> GRACE yet even though the seat is still
    // physically held (the student hasn't renewed and no admin has released
    // it). Deliberately keyed off LocalDate.now() (the real "today"), NOT the
    // viewed `date` param: this must NOT affect memberships that are legitimately
    // still on schedule when a future/past `date` is queried — those keep going
    // through the strict endDate >= date bound in getSeatMap().
    private boolean isHeldIndefinitely(Membership m) {
        return m.getStatus() == Membership.Status.GRACE
                || (m.getStatus() == Membership.Status.ACTIVE
                        && m.getEndDate().isBefore(LocalDate.now()));
    }

    // Every booking ever made against a seat, newest first — PENDING (checkout
    // abandoned before payment) and CANCELLED rows are excluded since they were
    // never a real occupancy. Known gap: AdminMembershipService.changeSeat()
    // mutates a Membership's seatNumber in place rather than creating a new row,
    // so a student moved off this seat via an explicit seat change won't appear
    // here — accepted limitation, not fixed by this method.
    public List<SeatHistoryEntryDto> getSeatHistory(String seatNumber) {
        List<Membership> rows = membershipRepository
                .findBySeatNumberOrderByStartDateDesc(seatNumber)
                .stream()
                .filter(m -> m.getStatus() != Membership.Status.PENDING
                          && m.getStatus() != Membership.Status.CANCELLED)
                .collect(Collectors.toList());

        Set<UUID> userIds = rows.stream().map(Membership::getUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return rows.stream()
                .map(m -> {
                    User u = userMap.get(m.getUserId());
                    return SeatHistoryEntryDto.builder()
                            .membershipId(m.getId().toString())
                            .studentName(u != null ? u.getName() : "Unknown")
                            .studentMobile(u != null ? u.getMobile() : null)
                            .shift(m.getShift())
                            .startDate(m.getStartDate().toString())
                            .endDate(m.getEndDate().toString())
                            .status(m.getStatus().name())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // Every membership this student has ever had, newest first — same
    // PENDING/CANCELLED exclusion and the same known gap as getSeatHistory:
    // an in-place seat change (AdminMembershipService.changeSeat) mutates the
    // existing membership's seatNumber rather than creating a new row, so it
    // won't show as two separate seats here, only the membership's current one.
    public List<SeatHistoryEntryDto> getStudentSeatHistory(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + userId));

        List<Membership> rows = membershipRepository
                .findByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .filter(m -> m.getStatus() != Membership.Status.PENDING
                          && m.getStatus() != Membership.Status.CANCELLED)
                .collect(Collectors.toList());

        Set<UUID> planIds = rows.stream().map(Membership::getPlanId).collect(Collectors.toSet());
        Map<UUID, Plan> planMap = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(Plan::getId, p -> p));

        return rows.stream()
                .map(m -> SeatHistoryEntryDto.builder()
                        .membershipId(m.getId().toString())
                        .studentName(user.getName())
                        .studentMobile(user.getMobile())
                        .shift(m.getShift())
                        .startDate(m.getStartDate().toString())
                        .endDate(m.getEndDate().toString())
                        .status(m.getStatus().name())
                        .seatNumber(m.getSeatNumber())
                        .planName(Optional.ofNullable(planMap.get(m.getPlanId())).map(Plan::getName).orElse(null))
                        .build())
                .collect(Collectors.toList());
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
                            .findFirstByUserIdAndStatusOrderByEndDateDesc(
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

    // ── Pending Fees ──────────────────────────────────────────────────────────

    public List<StudentDto> getStudentsWithPendingFees() {
        int graceDays = currentGraceDays();
        List<Payment> pending = paymentRepository.findByPendingAmountGreaterThan(BigDecimal.ZERO);
        Set<UUID> membershipIds = pending.stream().map(Payment::getMembershipId).collect(Collectors.toSet());
        if (membershipIds.isEmpty()) return List.of();

        List<Membership> memberships = membershipRepository.findAllById(membershipIds).stream()
                .filter(m -> m.getStatus() == Membership.Status.ACTIVE)
                .collect(Collectors.toList());

        Map<UUID, Payment> payByMem = pending.stream()
                .collect(Collectors.toMap(Payment::getMembershipId, p -> p, (a, b) -> a));

        Set<UUID> userIds = memberships.stream().map(Membership::getUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return memberships.stream()
                .filter(m -> userMap.containsKey(m.getUserId()))
                .map(m -> {
                    StudentDto dto = StudentDto.fromEntities(userMap.get(m.getUserId()), m);
                    Payment pay = payByMem.get(m.getId());
                    if (pay != null) {
                        dto.setPaymentMode("CASH".equalsIgnoreCase(pay.getPaymentGateway()) ? "CASH" : "ONLINE");
                        dto.setPendingAmount(pay.getPendingAmount());
                    }
                    dto.setDisplayStatus(StudentStatusResolver.resolve(m, pay, null, graceDays).name());
                    return dto;
                })
                .sorted(Comparator.comparing(StudentDto::getPendingAmount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // ── Orphaned Seats ────────────────────────────────────────────────────────
    // Students with an ACTIVE, paid membership but no matching SeatBooking —
    // see MembershipRepository.findActiveMembershipsWithoutSeatBooking() for
    // why this happens. Deliberately detection-only, no auto-repair: an admin
    // resolves each one manually via the existing Change Seat action, since an
    // automated fix could double-book a seat if it ever raced a genuine conflict.

    public List<StudentDto> getStudentsWithOrphanedSeats() {
        List<Membership> memberships = membershipRepository.findActiveMembershipsWithoutSeatBooking();
        if (memberships.isEmpty()) return List.of();

        Set<UUID> userIds = memberships.stream().map(Membership::getUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<UUID> planIds = memberships.stream().map(Membership::getPlanId).collect(Collectors.toSet());
        Map<UUID, Plan> planMap = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(Plan::getId, p -> p));

        return memberships.stream()
                .filter(m -> userMap.containsKey(m.getUserId()))
                .map(m -> {
                    StudentDto dto = StudentDto.fromEntities(userMap.get(m.getUserId()), m);
                    Plan plan = planMap.get(m.getPlanId());
                    if (plan != null) dto.setPlanName(plan.getName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void clearPendingFees(String userId, BigDecimal amountCleared) {
        UUID uid = UUID.fromString(userId);

        List<Payment> pendingPayments = paymentRepository
                .findByUserIdAndPendingAmountGreaterThan(uid, BigDecimal.ZERO);
        BigDecimal totalPending = pendingPayments.stream()
                .map(Payment::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPending.compareTo(BigDecimal.ZERO) <= 0) return; // nothing was actually pending
        if (amountCleared.compareTo(totalPending) > 0) {
            throw new IllegalArgumentException(
                    "Amount cleared (₹" + amountCleared + ") cannot exceed the pending amount (₹" + totalPending + ")");
        }
        BigDecimal remaining = totalPending.subtract(amountCleared);

        // Zero out the pending balance on the original rows — their `amount`
        // stays untouched, so each original payment still shows as its own
        // separate, unaltered entry in payment history. Any remainder is
        // carried forward on the new clearancePayment row below instead.
        paymentRepository.clearPendingAmountByUserId(uid);
        log.info("Pending fees cleared for user {} (₹{} recorded, ₹{} still pending)", userId, amountCleared, remaining);

        User user = userRepository.findById(uid).orElse(null);
        if (user == null) return;

        Optional<Membership> currentOrLatest = membershipRepository
                .findFirstByUserIdCurrentOrderByEndDateDesc(uid)
                .or(() -> membershipRepository.findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(uid, Membership.Status.PENDING));
        String seatNumber = currentOrLatest.map(Membership::getSeatNumber).orElse(null);
        String validUpto = currentOrLatest.map(Membership::getEndDate).map(Object::toString).orElse(null);
        UUID membershipIdForClearance = currentOrLatest.map(Membership::getId)
                .orElseGet(() -> pendingPayments.get(0).getMembershipId());

        String invoiceId = "INV-" + LocalDate.now().toString().replace("-", "") + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        // The cleared amount is a real, separate transaction that happened
        // TODAY — persist it as its own new Payment row (not folded into an
        // older row) so it shows up as its own payment-history entry and its
        // `createdAt` (set by @PrePersist) correctly counts toward today's
        // revenue instead of whatever day the original payment happened.
        Payment clearancePayment = Payment.builder()
                .membershipId(membershipIdForClearance)
                .userId(uid)
                .amount(amountCleared)
                .pendingAmount(remaining)
                .paymentGateway("CASH")
                .gatewayOrderId("cash_dues_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .status(Payment.Status.SUCCESS)
                .invoiceId(invoiceId)
                .build();
        paymentRepository.save(clearancePayment);

        RenewalReminderEvent adminEvent = RenewalReminderEvent.builder()
                .userId(uid.toString())
                .userName(user.getName())
                .userMobile(user.getMobile())
                .userEmail(user.getEmail())
                .seatNumber(seatNumber)
                .pendingAmount(amountCleared)
                .eventType("PENDING_FEE_CLEARED_ADMIN")
                .build();
        kafkaTemplate.send("renewal-reminder", uid.toString(), adminEvent);

        RenewalReminderEvent studentEvent = RenewalReminderEvent.builder()
                .userId(uid.toString())
                .userName(user.getName())
                .userMobile(user.getMobile())
                .userEmail(user.getEmail())
                .seatNumber(seatNumber)
                .pendingAmount(amountCleared)
                .eventType("PENDING_FEE_CLEARED")
                .build();
        kafkaTemplate.send("renewal-reminder", uid.toString(), studentEvent);

        PaymentReceiptEvent receiptEvent = PaymentReceiptEvent.builder()
                .userId(uid.toString())
                .userName(user.getName())
                .userMobile(user.getMobile())
                .userEmail(user.getEmail())
                .invoiceId(invoiceId)
                .paymentDate(LocalDate.now().toString())
                .amountPaid(amountCleared)
                .amountPending(remaining)
                .seatNumber(seatNumber)
                .validUpto(validUpto)
                .paymentMethod("CASH")
                .receiptType("DUES_CLEARED")
                .build();
        kafkaTemplate.send("payment-receipt", uid.toString(), receiptEvent);

        log.info("Pending fee cleared notifications queued for user '{}' (₹{})", user.getName(), amountCleared);
    }

    @Transactional
    public int sendPendingFeeReminders(List<String> userIds) {
        List<StudentDto> targets = userIds == null || userIds.isEmpty()
                ? getStudentsWithPendingFees()
                : userIds.stream()
                        .map(id -> {
                            try { return getStudentDetails(id); } catch (Exception e) { return null; }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        int sent = 0;
        for (StudentDto s : targets) {
            if (s.getPendingAmount() == null || s.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            RenewalReminderEvent event = RenewalReminderEvent.builder()
                    .userId(s.getId())
                    .userName(s.getName())
                    .userMobile(s.getMobile())
                    .userEmail(s.getEmail())
                    .seatNumber(s.getSeatNumber())
                    .pendingAmount(s.getPendingAmount())
                    .eventType("PENDING_FEE_REMINDER")
                    .build();
            kafkaTemplate.send("renewal-reminder", s.getId(), event);
            sent++;
            log.info("Pending fee reminder queued for user '{}' (₹{})", s.getName(), s.getPendingAmount());
        }
        return sent;
    }

    // ── Grace Period Dues ─────────────────────────────────────────────────────
    // Students whose membership has lapsed into GRACE (seat held, dues owed)
    // and who haven't cleared their dues yet — distinct from getStudentsWithPendingFees()
    // above, which covers a partial-cash-payment balance on an otherwise ACTIVE membership.

    public List<StudentDto> getStudentsInGraceWithDues() {
        int graceDays = currentGraceDays();
        List<Membership> memberships = membershipRepository.findByStatus(Membership.Status.GRACE).stream()
                .filter(m -> m.getDuesAmount() != null && m.getDuesAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (memberships.isEmpty()) return List.of();

        Set<UUID> userIds = memberships.stream().map(Membership::getUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return memberships.stream()
                .filter(m -> userMap.containsKey(m.getUserId()))
                .map(m -> {
                    StudentDto dto = StudentDto.fromEntities(userMap.get(m.getUserId()), m);
                    dto.setDisplayStatus(StudentStatusResolver.resolve(m, null, null, graceDays).name());
                    return dto;
                })
                .sorted(Comparator.comparing(StudentDto::getMembershipEnd))
                .collect(Collectors.toList());
    }

    @Transactional
    public int sendGraceDuesReminders(List<String> userIds) {
        List<StudentDto> targets = userIds == null || userIds.isEmpty()
                ? getStudentsInGraceWithDues()
                : userIds.stream()
                        .map(id -> {
                            try { return getStudentDetails(id); } catch (Exception e) { return null; }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        int sent = 0;
        for (StudentDto s : targets) {
            if (!"GRACE".equals(s.getMembershipStatus())) continue;
            if (s.getDuesAmount() == null || s.getDuesAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            RenewalReminderEvent event = RenewalReminderEvent.builder()
                    .userId(s.getId())
                    .userName(s.getName())
                    .userMobile(s.getMobile())
                    .userEmail(s.getEmail())
                    .seatNumber(s.getSeatNumber())
                    .expiryDate(s.getMembershipEnd())
                    .pendingAmount(s.getDuesAmount())
                    .eventType("GRACE_DUES_REMINDER")
                    .build();
            kafkaTemplate.send("renewal-reminder", s.getId(), event);
            sent++;
            log.info("Grace dues reminder queued for user '{}' (₹{})", s.getName(), s.getDuesAmount());
        }
        return sent;
    }

    // ── Revenue Report ────────────────────────────────────────────────────────

    public List<PaymentAmountBreakdownDto> getPaymentAmountBreakdown(String fromStr, String toStr) {
        LocalDateTime from = LocalDate.parse(fromStr).atStartOfDay();
        LocalDateTime to   = LocalDate.parse(toStr).atTime(23, 59, 59);
        return paymentRepository.countByAmountForPeriod(from, to).stream()
                .map(row -> PaymentAmountBreakdownDto.builder()
                        .amount((BigDecimal) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    public List<DailyPaymentDto> getPaymentsByDate(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        List<Payment> payments = paymentRepository.findSuccessfulPaymentsForDay(
                date.atStartOfDay(), date.atTime(23, 59, 59));

        Set<UUID> userIds = payments.stream().map(Payment::getUserId).collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return payments.stream().map(p -> {
            User user = usersById.get(p.getUserId());
            String ref = (p.getGatewayPaymentId() != null && !p.getGatewayPaymentId().isBlank())
                    ? p.getGatewayPaymentId()
                    : p.getGatewayOrderId();
            return DailyPaymentDto.builder()
                    .studentName(user != null ? user.getName() : "—")
                    .studentMobile(user != null ? user.getMobile() : "—")
                    .amount(p.getAmount())
                    .paymentGateway(p.getPaymentGateway())
                    .referenceId(ref)
                    .paidAt(p.getCreatedAt().toString())
                    .build();
        }).collect(Collectors.toList());
    }

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

    // ── Update Student Profile ────────────────────────────────────────────────

    @Transactional
    public StudentDto updateStudent(String userId, UpdateStudentRequest req) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + userId));
        if (req.getName()   != null && !req.getName().isBlank())   user.setName(req.getName().trim());
        if (req.getMobile() != null && !req.getMobile().isBlank()) user.setMobile(req.getMobile().trim());
        if (req.getEmail()  != null) user.setEmail(req.getEmail().isBlank() ? null : req.getEmail().trim());
        user.setAddress(req.getAddress() != null && !req.getAddress().isBlank() ? req.getAddress().trim() : null);
        user.setGender(req.getGender()   != null && !req.getGender().isBlank()  ? req.getGender().trim()   : null);
        user.setDateOfBirth(req.getDateOfBirth() != null && !req.getDateOfBirth().isBlank()
                ? LocalDate.parse(req.getDateOfBirth()) : null);
        if (req.getJoinedAt() != null && !req.getJoinedAt().isBlank()) {
            LocalDate newStart = LocalDate.parse(req.getJoinedAt());
            user.setCreatedAt(newStart.atStartOfDay());
            membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(user.getId(), Membership.Status.ACTIVE)
                .filter(m -> !m.getEndDate().isBefore(LocalDate.now()))
                .ifPresent(m -> planRepository.findById(m.getPlanId()).ifPresent(plan -> {
                    m.setStartDate(newStart);
                    m.setEndDate(newStart.plusDays(plan.getDurationDays()));
                    membershipRepository.save(m);
                }));
        }
        userRepository.save(user);
        return getStudentDetails(userId);
    }

    // ── Student Payment History ───────────────────────────────────────────────

    public List<PaymentHistoryDto> getStudentPayments(String userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                // ₹0 rows are "fully on credit" cash bookings (nothing paid yet) —
                // real pending-balance tracking, not an actual payment to show.
                .filter(p -> p.getAmount() != null && p.getAmount().signum() > 0)
                .map(p -> PaymentHistoryDto.builder()
                        .id(p.getId())
                        .membershipId(p.getMembershipId())
                        .amount(p.getAmount())
                        .paymentGateway(p.getPaymentGateway())
                        .gatewayOrderId(p.getGatewayOrderId())
                        .gatewayPaymentId(p.getGatewayPaymentId())
                        .status(p.getStatus() != null ? p.getStatus().name() : null)
                        .paidAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Delete Student ────────────────────────────────────────────────────────

    @Transactional
    public void deleteStudent(String userId) {
        UUID id = UUID.fromString(userId);
        userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + userId));
        feedbackRepository.deleteByUserId(id);    // has FK to users — must go first
        seatBookingRepository.deleteByUserId(id);
        paymentRepository.deleteByUserId(id);
        membershipRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        log.info("Student deleted: {}", userId);
    }

    // ── Direct Message ────────────────────────────────────────────────────────

    public void sendDirectMessage(String userId, BroadcastRequest req) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        BroadcastNotificationEvent event = BroadcastNotificationEvent.builder()
                .userId(user.getId().toString())
                .mobile(user.getMobile())
                .userName(user.getName())
                .message(req.getMessage())
                .isFirst(false)
                .build();
        kafkaTemplate.send("broadcast-notification", user.getId().toString(), event);
        log.info("Direct message queued for student: {}", userId);
    }

    // ── Broadcast Notification ────────────────────────────────────────────────

    public int broadcastNotification(BroadcastRequest req) {
        List<User> recipients = userRepository.findStudentsWithActiveMemberships();
        for (int i = 0; i < recipients.size(); i++) {
            User user = recipients.get(i);
            BroadcastNotificationEvent event = BroadcastNotificationEvent.builder()
                    .userId(user.getId().toString())
                    .mobile(user.getMobile())
                    .userName(user.getName())
                    .message(req.getMessage())
                    .isFirst(i == 0)
                    .build();
            try {
                kafkaTemplate.send("broadcast-notification", user.getId().toString(), event);
            } catch (Exception e) {
                log.warn("Failed to queue broadcast for user {}: {}", user.getId(), e.getMessage());
            }
        }
        broadcastMessageRepository.save(BroadcastMessage.builder()
                .message(req.getMessage())
                .recipientCount(recipients.size())
                .build());
        log.info("Broadcast queued for {} active members", recipients.size());
        return recipients.size();
    }

    public List<BroadcastHistoryDto> getBroadcastHistory() {
        return broadcastMessageRepository.findTop5ByOrderBySentAtDesc()
                .stream()
                .map(BroadcastHistoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Settings-backed values ────────────────────────────────────────────────

    private int currentGraceDays() {
        return appSettingsRepository.findById(1L)
                .map(AppSettings::getGraceDays)
                .orElse(AppSettingsService.DEFAULT_GRACE_DAYS);
    }
}