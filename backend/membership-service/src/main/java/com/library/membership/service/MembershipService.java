package com.library.membership.service;

import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.PaymentDto;
import com.library.membership.dto.UserApiResponse;
import com.library.membership.dto.UserProfileDto;
import com.library.membership.entity.Membership;
import com.library.membership.event.SeatAssistanceEvent;
import com.library.membership.repository.MembershipRepository;
import com.library.membership.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final MembershipRepository          membershipRepository;
    private final PaymentRepository             paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate                  restTemplate;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    /**
     * Returns the student's current ACTIVE membership, or their GRACE membership
     * (lapsed, seat held, dues owed) if that's what they have instead — the
     * frontend distinguishes the two via the `status` field and shows either the
     * normal membership card or the "pay dues" banner.
     * Returns null (not an exception) if neither exists — the frontend checks
     * for null and shows the "Get a plan" CTA.
     */
    public MembershipDto getUserActiveMembership(String userId) {
        UUID uid = UUID.fromString(userId);
        return membershipRepository.findActiveByUserId(uid)
                .or(() -> membershipRepository.findGraceByUserId(uid))
                .map(MembershipDto::fromEntity)
                .orElse(null);
    }

    public MembershipDto getUserQueuedMembership(String userId) {
        return membershipRepository
                .findQueuedByUserId(UUID.fromString(userId))
                .map(MembershipDto::fromEntity)
                .orElse(null);
    }

    /**
     * Returns full membership history for a student — all statuses
     * (PENDING, ACTIVE, EXPIRED, CANCELLED), newest first.
     * Used by MembershipPage.jsx history section.
     */
    public List<MembershipDto> getUserMemberships(String userId) {
        return membershipRepository
                .findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                .map(MembershipDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Returns all payment records for a student, newest first.
     * Used by GET /api/payments/my — called by MembershipPage.jsx
     * to display payment history with Razorpay order/payment IDs.
     */
    public List<PaymentDto> getUserPayments(String userId) {
        return paymentRepository
                .findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                // ₹0 rows aren't real payments — nothing to show the student.
                .filter(p -> p.getAmount() != null && p.getAmount().signum() > 0)
                .map(PaymentDto::fromEntity)
                .collect(Collectors.toList());
    }

    public void callAdmin(String userId) {
        Membership membership = membershipRepository
                .findActiveByUserId(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("No active membership found"));

        if (membership.getSeatNumber() == null || membership.getSeatNumber().isBlank()) {
            throw new IllegalArgumentException("No seat assigned to your membership");
        }

        UserProfileDto user = fetchUserProfile(userId);

        SeatAssistanceEvent event = SeatAssistanceEvent.builder()
                .userId(userId)
                .userName(user.getName() != null ? user.getName() : "Student")
                .seatNumber(membership.getSeatNumber())
                .eventType("SEAT_ASSISTANCE")
                .adminMobile(fetchAdminMobile(userId))
                .build();

        kafkaTemplate.send("seat-assistance", userId, event);
        log.info("Published seat-assistance event for user: {} seat: {}",
                userId, membership.getSeatNumber());
    }

    private UserProfileDto fetchUserProfile(String userId) {
        try {
            String url = userServiceBaseUrl + "/api/users/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId);
            headers.set("X-User-Role", "STUDENT");
            ResponseEntity<UserApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), UserApiResponse.class);
            if (resp.getBody() != null && resp.getBody().getData() != null) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user profile (userId={}): {}", userId, e.getMessage());
        }
        return new UserProfileDto();
    }

    private String fetchAdminMobile(String callerUserId) {
        try {
            String url = userServiceBaseUrl + "/api/users/admin-contact";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", callerUserId);
            headers.set("X-User-Role", "STUDENT");
            ResponseEntity<UserApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), UserApiResponse.class);
            if (resp.getBody() != null && resp.getBody().getData() != null) {
                return resp.getBody().getData().getMobile();
            }
        } catch (Exception e) {
            log.warn("Could not fetch admin contact: {}", e.getMessage());
        }
        return null;
    }
}