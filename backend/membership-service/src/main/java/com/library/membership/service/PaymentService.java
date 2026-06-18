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
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final MembershipRepository          membershipRepository;
    private final PaymentRepository             paymentRepository;
    private final PlanRepository                planRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate                  restTemplate;

    @Value("${app.payment-gateway:CASHFREE}")
    private String activeGateway;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    // Razorpay
    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    // Cashfree
    @Value("${cashfree.app-id:}")
    private String cashfreeAppId;

    @Value("${cashfree.secret-key:}")
    private String cashfreeSecretKey;

    @Value("${cashfree.env:sandbox}")
    private String cashfreeEnv;

    // ── Create Order ──────────────────────────────────────────────────────────

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

        // 5. Create gateway order — or generate a dev mock if credentials absent
        String gatewayOrderId;
        String paymentSessionId = null;

        if ("RAZORPAY".equals(activeGateway)) {
            gatewayOrderId = createRazorpayOrder(plan, membership);
        } else {
            // CASHFREE (default)
            String[] cashfreeResult = createCashfreeOrder(plan, membership, userId);
            gatewayOrderId   = cashfreeResult[0];
            paymentSessionId = cashfreeResult[1]; // null in dev mode
        }

        // 6. Persist payment record in PENDING state
        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(UUID.fromString(userId))
                .amount(plan.getPrice())
                .paymentGateway(activeGateway)
                .gatewayOrderId(gatewayOrderId)
                .status(Payment.Status.PENDING)
                .build();
        paymentRepository.save(payment);

        // 7. Return order details to frontend
        return CreateOrderResponse.builder()
                .orderId(gatewayOrderId)
                .membershipId(membership.getId().toString())
                .amount(plan.getPrice())
                .currency("INR")
                .gateway(activeGateway)
                .paymentSessionId(paymentSessionId)
                .razorpayKeyId("RAZORPAY".equals(activeGateway) ? razorpayKeyId : null)
                .build();
    }

    // ── Verify Payment + Activate Membership ──────────────────────────────────

    @Transactional
    public MembershipDto verifyAndActivateMembership(String userId,
                                                     PaymentVerifyRequest request) {

        // 1. Gateway-specific verification (skipped in dev mode)
        if ("RAZORPAY".equals(activeGateway)) {
            if (!razorpayKeySecret.isBlank()
                    && request.getSignature() != null
                    && !request.getGatewayOrderId().startsWith("dev_")) {
                verifyRazorpaySignature(
                        request.getGatewayOrderId(),
                        request.getGatewayPaymentId(),
                        request.getSignature()
                );
            }
        } else {
            // CASHFREE: verify order status via API poll
            if (!cashfreeSecretKey.isBlank()
                    && !request.getGatewayOrderId().startsWith("dev_")) {
                verifyCashfreeOrder(request.getGatewayOrderId());
            }
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

        // 4. Publish Kafka event → notification-service sends WhatsApp + email
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

    // ── Razorpay Helpers ──────────────────────────────────────────────────────

    private String createRazorpayOrder(Plan plan, Membership membership) {
        if (!razorpayKeyId.isBlank()) {
            try {
                RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

                JSONObject options = new JSONObject();
                options.put("amount",
                        plan.getPrice().multiply(BigDecimal.valueOf(100)).intValue());
                options.put("currency", "INR");
                options.put("receipt",
                        "lib_" + membership.getId().toString().substring(0, 8));

                Order order = client.orders.create(options);
                String orderId = order.get("id");

                log.info("Razorpay order created: {} for membership: {}",
                        orderId, membership.getId());
                return orderId;

            } catch (Exception e) {
                log.error("Razorpay order creation failed: {}", e.getMessage());
                throw new RuntimeException("Payment gateway error. Please try again.");
            }
        } else {
            String orderId = "dev_order_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DEV MODE: Fake Razorpay order: {}", orderId);
            return orderId;
        }
    }

    // Signature: HMAC_SHA256(orderId + "|" + paymentId, keySecret)
    private void verifyRazorpaySignature(String orderId, String paymentId, String signature) {
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

    // ── Cashfree Helpers ──────────────────────────────────────────────────────

    // Returns [gatewayOrderId, paymentSessionId] — paymentSessionId is null in dev mode
    private String[] createCashfreeOrder(Plan plan, Membership membership, String userId) {
        if (!cashfreeAppId.isBlank()) {
            try {
                UserProfileDto user = fetchUserForCashfree(userId);

                Map<String, Object> customerDetails = new HashMap<>();
                customerDetails.put("customer_id",    userId);
                customerDetails.put("customer_name",  user.getName()   != null && !user.getName().isBlank()   ? user.getName()   : "Library Student");
                customerDetails.put("customer_phone", user.getMobile() != null && !user.getMobile().isBlank() ? user.getMobile() : "9999999999");
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    customerDetails.put("customer_email", user.getEmail());
                }

                Map<String, Object> body = Map.of(
                        "order_id",         "lib_" + membership.getId().toString().substring(0, 8),
                        "order_amount",     plan.getPrice(),
                        "order_currency",   "INR",
                        "customer_details", customerDetails
                );

                CashfreeOrderResponse resp = restTemplate.postForObject(
                        cashfreeBaseUrl() + "/pg/orders",
                        new HttpEntity<>(body, cashfreeHeaders()),
                        CashfreeOrderResponse.class
                );

                if (resp == null) throw new RuntimeException("Empty response from Cashfree");

                log.info("Cashfree order created: {} (cf_order_id={}) for membership: {}",
                        resp.getOrderId(), resp.getCfOrderId(), membership.getId());

                return new String[]{ resp.getCfOrderId(), resp.getPaymentSessionId() };

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("Cashfree order creation failed: {}", e.getMessage());
                throw new RuntimeException("Payment gateway error. Please try again.");
            }
        } else {
            String orderId = "dev_order_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DEV MODE: Fake Cashfree order: {}", orderId);
            return new String[]{ orderId, null };
        }
    }

    private void verifyCashfreeOrder(String orderId) {
        try {
            ResponseEntity<CashfreeOrderResponse> resp = restTemplate.exchange(
                    cashfreeBaseUrl() + "/pg/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(cashfreeHeaders()),
                    CashfreeOrderResponse.class
            );
            CashfreeOrderResponse body = resp.getBody();
            if (body == null || !"PAID".equals(body.getOrderStatus())) {
                String status = body != null ? body.getOrderStatus() : "null";
                throw new IllegalArgumentException(
                        "Cashfree payment not completed (order_status: " + status + ")");
            }
            log.info("Cashfree order verified successfully: {}", orderId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cashfree verification error: " + e.getMessage());
        }
    }

    private HttpHeaders cashfreeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", cashfreeAppId);
        headers.set("x-client-secret", cashfreeSecretKey);
        headers.set("x-api-version", "2023-08-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String cashfreeBaseUrl() {
        return "production".equals(cashfreeEnv)
                ? "https://api.cashfree.com"
                : "https://sandbox.cashfree.com";
    }

    private UserProfileDto fetchUserForCashfree(String userId) {
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
            log.warn("Could not fetch user details for Cashfree order (userId={}): {}",
                    userId, e.getMessage());
        }
        return new UserProfileDto();
    }
}
