package com.library.admin.scheduler;

import com.library.admin.dto.RenewalReminderEvent;
import com.library.admin.entity.Membership;
import com.library.admin.entity.User;
import com.library.admin.repository.MembershipRepository;
import com.library.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryReminderScheduler {

    private final MembershipRepository          membershipRepository;
    private final UserRepository                userRepository;
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
}