package com.library.membership.service;

import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.PaymentDto;
import com.library.membership.repository.MembershipRepository;
import com.library.membership.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final PaymentRepository    paymentRepository;

    /**
     * Returns the student's current ACTIVE membership.
     * Returns null (not an exception) if no active membership exists —
     * the frontend checks for null and shows the "Get a plan" CTA.
     */
    public MembershipDto getUserActiveMembership(String userId) {
        return membershipRepository
                .findActiveByUserId(UUID.fromString(userId))
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
                .map(PaymentDto::fromEntity)
                .collect(Collectors.toList());
    }
}