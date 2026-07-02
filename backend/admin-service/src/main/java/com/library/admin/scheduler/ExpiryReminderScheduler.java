package com.library.admin.scheduler;

import com.library.admin.dto.RenewalReminderEvent;
import com.library.admin.entity.Membership;
import com.library.admin.entity.Plan;
import com.library.admin.entity.SeatBooking;
import com.library.admin.entity.User;
import com.library.admin.repository.MembershipRepository;
import com.library.admin.repository.PlanRepository;
import com.library.admin.repository.SeatBookingRepository;
import com.library.admin.repository.UserRepository;
import com.library.admin.service.AdminMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryReminderScheduler {

    // A membership held in GRACE never expires on its own — SeatBooking.endDate is
    // pushed out to this sentinel so seat-service's date-range availability query
    // keeps blocking the seat indefinitely. Only an explicit admin release (which
    // sets SeatBooking.status = RELEASED) frees it. Never used as a cache-bust loop
    // bound — see AdminMembershipService.invalidateSeatCache().
    private static final LocalDate SEAT_HOLD_SENTINEL = LocalDate.of(9999, 12, 31);

    private final MembershipRepository          membershipRepository;
    private final UserRepository                userRepository;
    private final PlanRepository                planRepository;
    private final SeatBookingRepository         seatBookingRepository;
    private final AdminMembershipService        adminMembershipService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Runs every day at 9:00 AM.
     *
     * Logic:
     *  1. Query all ACTIVE memberships expiring within 7 days where reminder_sent = false.
     *  2. For each, compute exact daysLeft.
     *  3. Only publish a Kafka event at the 7-day and 3-day checkpoints to avoid
     *     spamming students every day.
     *  4. After publishing, set reminder_sent = true on the membership so the
     *     scheduler never fires again for that membership record.
     *
     * Note: admin-service runs with replicas = 1 in Kubernetes specifically to
     * ensure this scheduled task only executes on one pod. If you scale to multiple
     * replicas, use ShedLock or a distributed lock to prevent duplicate sends.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendExpiryReminders() {
        log.info("ExpiryReminderScheduler triggered at {}", LocalDate.now());

        LocalDate today      = LocalDate.now();
        LocalDate sevenDays  = today.plusDays(7);

        // Fetch ACTIVE memberships expiring within 7 days, reminder_sent = false only
        List<Membership> candidates = membershipRepository
                .findExpiringMemberships(today, sevenDays);

        if (candidates.isEmpty()) {
            log.info("No memberships approaching expiry — nothing to send.");
            return;
        }

        log.info("Found {} candidate memberships approaching expiry", candidates.size());

        // Bulk-load users to avoid N+1 queries
        Set<UUID> userIds = candidates.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        int sent = 0;

        for (Membership mem : candidates) {
            User user = userMap.get(mem.getUserId());
            if (user == null) {
                log.warn("User not found for membership: {}", mem.getId());
                continue;
            }

            long daysLeft = ChronoUnit.DAYS.between(today, mem.getEndDate());

            // Only fire at 7-day and 3-day thresholds.
            // On any other day (e.g. day 6, 5, 4, 2, 1) we skip but do NOT mark
            // reminder_sent = true, so the scheduler will check again tomorrow.
            if (daysLeft != 7 && daysLeft != 3) {
                log.debug("Skipping membership {} — {} days left (not a trigger day)",
                        mem.getId(), daysLeft);
                continue;
            }

            // Build the Kafka event
            RenewalReminderEvent event = RenewalReminderEvent.builder()
                    .userId(mem.getUserId().toString())
                    .membershipId(mem.getId().toString())
                    .userName(user.getName())
                    .userMobile(user.getMobile())
                    .userEmail(user.getEmail())
                    .seatNumber(mem.getSeatNumber())
                    .expiryDate(mem.getEndDate().toString())
                    .daysRemaining((int) daysLeft)
                    .eventType("RENEWAL_REMINDER")
                    .build();

            // Publish to Kafka → notification-service sends WhatsApp + email
            kafkaTemplate.send("renewal-reminder", mem.getUserId().toString(), event);

            // Mark reminder sent so this membership is not processed again
            // (the DB query filters WHERE reminder_sent = false)
            mem.setReminderSent(true);
            membershipRepository.save(mem);

            sent++;
            log.info("Renewal reminder queued for user '{}' — {} days left until expiry",
                    user.getName(), daysLeft);
        }

        log.info("ExpiryReminderScheduler completed — {} reminders published to Kafka", sent);
    }

    /**
     * Runs every day at 10:00 AM (after the 9 AM reminder run).
     * Finds memberships that are still ACTIVE but whose endDate has passed.
     *
     * If the student already pre-paid a renewal (a QUEUED membership exists),
     * this is the normal happy path: the old membership is finalized EXPIRED and
     * the queued one takes over immediately — no grace period, no dues.
     *
     * Otherwise the membership enters a GRACE period: dues equal to the plan's
     * current price are charged, the seat is held indefinitely (SeatBooking.endDate
     * pushed to SEAT_HOLD_SENTINEL so it can never free itself via the normal
     * date-range availability check), and both the student and admin are notified.
     * The seat is only freed again by an explicit admin "Release Seat" action.
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void markExpiredAndStartGrace() {
        LocalDate today = LocalDate.now();
        List<Membership> expired = membershipRepository.findExpiredActive(today);

        if (expired.isEmpty()) {
            log.info("SeatExpiredCheck: no newly expired memberships.");
            return;
        }

        Set<UUID> userIds = expired.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        int graced = 0;

        for (Membership mem : expired) {
            Optional<Membership> queuedOpt = membershipRepository.findQueuedByUserId(mem.getUserId());

            if (queuedOpt.isPresent()) {
                // Happy path — student already renewed in advance. Unchanged from
                // the pre-grace-period behaviour.
                mem.setStatus(Membership.Status.EXPIRED);
                membershipRepository.save(mem);

                Membership queued = queuedOpt.get();
                queued.setStatus(Membership.Status.ACTIVE);
                membershipRepository.save(queued);
                log.info("Activated queued plan for user {} — seat {} from {}",
                        mem.getUserId(), queued.getSeatNumber(), queued.getStartDate());
                continue;
            }

            User user = userMap.get(mem.getUserId());
            String userName = user != null ? user.getName()   : "Unknown";
            String mobile   = user != null ? user.getMobile() : null;
            String email    = user != null ? user.getEmail()  : null;

            BigDecimal duesAmount = planRepository.findById(mem.getPlanId())
                    .map(Plan::getPrice)
                    .orElseGet(() -> {
                        log.warn("Plan {} not found for membership {} — dues set to 0",
                                mem.getPlanId(), mem.getId());
                        return BigDecimal.ZERO;
                    });

            mem.setStatus(Membership.Status.GRACE);
            mem.setDuesAmount(duesAmount);
            membershipRepository.save(mem);

            seatBookingRepository.findFirstByMembershipIdAndStatus(mem.getId(), SeatBooking.Status.ACTIVE)
                    .ifPresent(booking -> {
                        booking.setEndDate(SEAT_HOLD_SENTINEL);
                        seatBookingRepository.save(booking);
                    });

            // Bounded — never invalidate all the way out to SEAT_HOLD_SENTINEL.
            adminMembershipService.invalidateSeatCache(mem.getShift(), today, today.plusDays(14));

            RenewalReminderEvent adminEvent = RenewalReminderEvent.builder()
                    .userId(mem.getUserId().toString())
                    .membershipId(mem.getId().toString())
                    .userName(userName)
                    .userMobile(mobile)
                    .userEmail(email)
                    .seatNumber(mem.getSeatNumber())
                    .expiryDate(mem.getEndDate().toString())
                    .daysRemaining(0)
                    .eventType("MEMBERSHIP_GRACE_STARTED")
                    .pendingAmount(duesAmount)
                    .build();
            kafkaTemplate.send("renewal-reminder", mem.getUserId().toString(), adminEvent);

            RenewalReminderEvent studentEvent = RenewalReminderEvent.builder()
                    .userId(mem.getUserId().toString())
                    .membershipId(mem.getId().toString())
                    .userName(userName)
                    .userMobile(mobile)
                    .userEmail(email)
                    .seatNumber(mem.getSeatNumber())
                    .expiryDate(mem.getEndDate().toString())
                    .daysRemaining(0)
                    .eventType("MEMBERSHIP_EXPIRED_GRACE")
                    .pendingAmount(duesAmount)
                    .build();
            kafkaTemplate.send("renewal-reminder", mem.getUserId().toString(), studentEvent);

            graced++;
            log.info("Seat {} held in GRACE for user '{}' — dues {} — student + admin notified",
                    mem.getSeatNumber(), userName, duesAmount);
        }

        log.info("SeatExpiredCheck: {} memberships entered GRACE, {} total processed",
                graced, expired.size());
    }
}