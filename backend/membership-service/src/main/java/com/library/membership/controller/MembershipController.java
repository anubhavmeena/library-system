package com.library.membership.controller;

import com.library.membership.dto.*;
import com.library.membership.service.IdCardService;
import com.library.membership.service.MembershipService;
import com.library.common.dto.ApiResponse;
import com.library.membership.service.PaymentService;
import com.library.membership.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MembershipController {

    private final MembershipService membershipService;
    private final PlanService       planService;
    private final PaymentService    paymentService;
    private final IdCardService     idCardService;

    // ── Plans (public — no auth needed) ───────────────────────────────────────

    @GetMapping("/api/plans")
    public ResponseEntity<ApiResponse<List<PlanDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getAllActivePlans()));
    }

    // ── Active membership ──────────────────────────────────────────────────────
    // X-User-Id injected by API Gateway after JWT validation

    @GetMapping("/api/memberships/my")
    public ResponseEntity<ApiResponse<MembershipDto>> getMyActiveMembership(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getUserActiveMembership(userId)));
    }

    // ── Download student ID card as PDF ───────────────────────────────────────

    @GetMapping(value = "/api/memberships/my/id-card", produces = "application/pdf")
    public ResponseEntity<byte[]> downloadIdCard(
            @RequestHeader("X-User-Id") String userId) {
        byte[] pdf = idCardService.generateIdCard(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"id-card.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // ── Full membership history ────────────────────────────────────────────────

    @GetMapping("/api/memberships/my/queued")
    public ResponseEntity<ApiResponse<MembershipDto>> getMyQueuedMembership(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getUserQueuedMembership(userId)));
    }

    @GetMapping("/api/memberships/my/all")
    public ResponseEntity<ApiResponse<List<MembershipDto>>> getAllMyMemberships(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getUserMemberships(userId)));
    }

    // ── Payment history (called by MembershipPage.jsx) ─────────────────────────

    @GetMapping("/api/payments/my")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getMyPayments(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getUserPayments(userId)));
    }

    // ── Create Razorpay order ──────────────────────────────────────────────────

    @PostMapping("/api/payments/create-order")
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.createOrder(userId, request)));
    }

    // ── Verify payment + activate membership ───────────────────────────────────

    @PostMapping("/api/payments/verify")
    public ResponseEntity<ApiResponse<MembershipDto>> verifyPayment(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PaymentVerifyRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(paymentService.verifyAndActivateMembership(userId, request)));
    }
}