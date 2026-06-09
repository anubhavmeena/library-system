package com.library.membership.service;

import com.library.membership.dto.*;
import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;
import com.library.membership.entity.Plan;
import com.library.membership.event.BookingConfirmedEvent;
import com.library.membership.exception.ResourceNotFoundException;
import com.library.membership.repository.MembershipRepository;
import com.library.membership.repository.PaymentRepository;
import com.library.membership.repository.PlanRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final MembershipRepository          membershipRepository;
    private final PaymentRepository             paymentRepository;
    private final PlanRepository                planRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    // ── Create Razorpay Order ─────────────────────────────────────────────────

    @Transactional
    public CreateOrderResponse createOrder(String userId, CreateOrderRequest request) {

        // 1. Block if user already has an active, non-expired membership
        membershipRepository.findActiveByUserId(UUID.fromString(userId))
                .filter(m -> !m.getEndDate().isBefore(LocalDate.now()))
                .ifPresent(m -> {
                    throw new IllegalArgumentException(
                            "You already have an active membership until " + m.getEndDate()
                            + ". You can purchase a new plan after it expires.");
                });

        // 2. Resolve the plan
        Plan plan = planRepository.findById(UUID.fromString(request.getPlanId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plan not found: " + request.getPlanId()));

        // 3. Validate shift requirement for half-day plans
        if (plan.getPlanType() == Plan.PlanType.HALF_DAY) {
            if (request.getShift() == null ||
                    (!request.getShift().equals("MORNING") && !request.getShift().equals("EVENING"))) {
                throw new IllegalArgumentException(
                        "Half-day plan requires shift selection: MORNING or EVENING");
            }
        }

        // 4. Create membership in PENDING state (activated after payment)
        Membership membership = Membership.builder()
                .userId(UUID.fromString(userId))
                .plan(plan)
                .seatId(request.getSeatId() != null
                        ? UUID.fromString(request.getSeatId()) : null)
                .seatNumber(request.getSeatNumber())
                .shift(plan.getPlanType() == Plan.PlanType.FULL_DAY
                        ? "FULL_DAY" : request.getShift())
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(plan.getDurationDays()))
                .status(Membership.Status.PENDING)
                .build();

        membership = membershipRepository.save(membership);

        // 5. Create Razorpay order — or generate a dev mock if credentials absent
        String gatewayOrderId;
        if (!razorpayKeyId.isBlank()) {
            try {
                RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

                JSONObject options = new JSONObject();
                // Razorpay expects amount in paise (1 INR = 100 paise)
                options.put("amount",
                        plan.getPrice().multiply(BigDecimal.valueOf(100)).intValue());
                options.put("currency", "INR");
                options.put("receipt",
                        "lib_" + membership.getId().toString().substring(0, 8));

                Order order    = client.orders.create(options);
                gatewayOrderId = order.get("id");

                log.info("Razorpay order created: {} for membership: {}",
                        gatewayOrderId, membership.getId());

            } catch (Exception e) {
                log.error("Razorpay order creation failed: {}", e.getMessage());
                throw new RuntimeException("Payment gateway error. Please try again.");
            }
        } else {
            // Dev mode: prefix dev_ so verifyAndActivateMembership skips HMAC check
            gatewayOrderId = "dev_order_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DEV MODE: Fake Razorpay order: {}", gatewayOrderId);
        }

        // 6. Persist payment record in PENDING state
        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(UUID.fromString(userId))
                .amount(plan.getPrice())
                .gatewayOrderId(gatewayOrderId)
                .status(Payment.Status.PENDING)
                .build();
        paymentRepository.save(payment);

        // 7. Return order details to frontend so it can open Razorpay checkout
        return CreateOrderResponse.builder()
                .orderId(gatewayOrderId)
                .membershipId(membership.getId().toString())
                .amount(plan.getPrice())
                .currency("INR")
                .razorpayKeyId(razorpayKeyId)
                .build();
    }

    // ── Verify Payment + Activate Membership ──────────────────────────────────

    @Transactional
    public MembershipDto verifyAndActivateMembership(String userId,
                                                     PaymentVerifyRequest request) {

        // 1. Skip HMAC verification in dev mode (dev_ prefix) or if not configured
        if (!razorpayKeySecret.isBlank()
                && request.getSignature() != null
                && !request.getGatewayOrderId().startsWith("dev_")) {
            verifySignature(
                    request.getGatewayOrderId(),
                    request.getGatewayPaymentId(),
                    request.getSignature()
            );
        }

        // 2. Update payment → SUCCESS
        Payment payment = paymentRepository
                .findByGatewayOrderId(request.getGatewayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found"));

        payment.setGatewayPaymentId(request.getGatewayPaymentId());
        payment.setStatus(Payment.Status.SUCCESS);
        paymentRepository.save(payment);

        // 3. Activate the membership
        Membership membership = membershipRepository
                .findById(payment.getMembershipId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        membership.setStatus(Membership.Status.ACTIVE);
        membership = membershipRepository.save(membership);

        // 4. Publish Kafka event → notification-service will send
        //    WhatsApp + email to student and an alert email to admin.
        //    Note: userName/userMobile/userEmail are left null here.
        //    notification-service fetches them from user-service using userId
        //    to avoid coupling membership-service to user-service.
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .userId(userId)
                .membershipId(membership.getId().toString())
                .planName(membership.getPlan().getName())
                .planType(membership.getPlan().getPlanType().name())
                .seatNumber(membership.getSeatNumber())
                .shift(membership.getShift())
                .startDate(membership.getStartDate().toString())
                .endDate(membership.getEndDate().toString())
                .amountPaid(payment.getAmount())
                .eventType("BOOKING_CONFIRMED")
                .build();

        kafkaTemplate.send("booking-confirmed", userId, event);
        log.info("Published booking-confirmed Kafka event for user: {} membership: {}",
                userId, membership.getId());

        return MembershipDto.fromEntity(membership);
    }

    // ── HMAC-SHA256 Signature Verification ────────────────────────────────────
    // Razorpay signs responses as: HMAC_SHA256(orderId + "|" + paymentId, keySecret)
    // We verify this to confirm the payment callback is genuinely from Razorpay.

    private void verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac    mac     = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(), "HmacSHA256"));
            String generated = HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));

            if (!generated.equals(signature)) {
                throw new IllegalArgumentException(
                        "Payment signature verification failed — possible tampering detected.");
            }
            log.info("Razorpay signature verified successfully for order: {}", orderId);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Signature verification error: " + e.getMessage());
        }
    }
}