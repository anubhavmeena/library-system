package com.library.membership.service;

import com.library.membership.dto.*;
import com.library.membership.entity.AppSettings;
import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;
import com.library.membership.entity.Plan;
import com.library.membership.event.BookingConfirmedEvent;
import com.library.membership.exception.ResourceNotFoundException;
import com.library.membership.repository.AppSettingsRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final MembershipRepository          membershipRepository;
    private final PaymentRepository             paymentRepository;
    private final PlanRepository                planRepository;
    private final AppSettingsRepository         appSettingsRepository;
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

        // 0. A membership in GRACE (lapsed, seat held, dues owed) must be paid off
        // or released by an admin before a brand-new plan can be purchased —
        // otherwise the student could end up with two seats.
        membershipRepository.findGraceByUserId(UUID.fromString(userId)).ifPresent(g -> {
            throw new IllegalArgumentException(
                    "You have an overdue membership with pending dues of " + g.getDuesAmount() +
                    ". Please clear your dues (or contact the library) before booking a new plan.");
        });

        // 1. Resolve the plan
        Plan plan = planRepository.findById(UUID.fromString(request.getPlanId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plan not found: " + request.getPlanId()));

        // 2. Determine start date — queue on top of active membership if one exists
        Optional<Membership> activeOpt = membershipRepository
                .findActiveByUserId(UUID.fromString(userId))
                .filter(m -> !m.getEndDate().isBefore(LocalDate.now()));

        LocalDate startDate;
        if (activeOpt.isPresent()) {
            // Only one queued plan allowed at a time
            membershipRepository.findQueuedByUserId(UUID.fromString(userId)).ifPresent(q -> {
                throw new IllegalArgumentException(
                        "You already have a plan queued starting " + q.getStartDate() + ".");
            });
            Membership active = activeOpt.get();
            startDate = active.getEndDate().plusDays(1);
            // Inherit seat and shift from current active membership
            if (active.getSeatId()     != null) request.setSeatId(active.getSeatId().toString());
            if (active.getSeatNumber() != null) request.setSeatNumber(active.getSeatNumber());
            if (active.getShift()      != null) request.setShift(active.getShift());
        } else {
            startDate = LocalDate.now();
        }

        // 3. Validate shift requirement for half-day plans
        if (plan.getPlanType() == Plan.PlanType.HALF_DAY) {
            if (request.getShift() == null ||
                    (!request.getShift().equals("MORNING") && !request.getShift().equals("EVENING"))) {
                throw new IllegalArgumentException(
                        "Half-day plan requires shift selection: MORNING or EVENING");
            }
        }

        // 4. Create membership in PENDING state (set to ACTIVE or QUEUED after payment)
        Membership membership = Membership.builder()
                .userId(UUID.fromString(userId))
                .plan(plan)
                .seatId(request.getSeatId() != null
                        ? UUID.fromString(request.getSeatId()) : null)
                .seatNumber(request.getSeatNumber())
                .shift(plan.getPlanType() == Plan.PlanType.FULL_DAY
                        ? "FULL_DAY" : request.getShift())
                .startDate(startDate)
                .endDate(startDate.plusDays(plan.getDurationDays()))
                .status(Membership.Status.PENDING)
                .build();

        membership = membershipRepository.save(membership);

        // 4b. Add the admin-configured convenience fee on top of the plan price —
        // only for a fresh plan purchase/renewal via the payment gateway, not for
        // dues payment (see createDuesOrder/verifyAndPayDues below, which charge
        // the raw dues amount — that's debt clearance, not a "plan fee").
        BigDecimal convenienceFee = appSettingsRepository.findById(1L)
                .map(AppSettings::getConvenienceFee)
                .orElse(BigDecimal.ZERO);
        BigDecimal chargeAmount = plan.getPrice().add(convenienceFee);

        // 5. Create gateway order — or generate a dev mock if credentials absent
        String gatewayOrderId;
        String paymentSessionId = null;

        if ("RAZORPAY".equals(activeGateway)) {
            gatewayOrderId = createRazorpayOrder(chargeAmount, membership);
        } else {
            // CASHFREE (default)
            String[] cashfreeResult = createCashfreeOrder(chargeAmount, membership, userId);
            gatewayOrderId   = cashfreeResult[0];
            paymentSessionId = cashfreeResult[1]; // null in dev mode
        }

        // 6. Persist payment record in PENDING state
        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(UUID.fromString(userId))
                .amount(chargeAmount)
                .paymentGateway(activeGateway)
                .gatewayOrderId(gatewayOrderId)
                .status(Payment.Status.PENDING)
                .build();
        paymentRepository.save(payment);

        // 7. Return order details to frontend
        return CreateOrderResponse.builder()
                .orderId(gatewayOrderId)
                .membershipId(membership.getId().toString())
                .amount(chargeAmount)
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

        verifyGatewayPayment(request);

        // 2. Update payment → SUCCESS
        Payment payment = paymentRepository
                .findByGatewayOrderId(request.getGatewayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found"));

        payment.setGatewayPaymentId(request.getGatewayPaymentId());
        payment.setStatus(Payment.Status.SUCCESS);
        paymentRepository.save(payment);

        // 3. Activate or queue the membership depending on its start date
        Membership membership = membershipRepository
                .findById(payment.getMembershipId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        boolean isQueued = membership.getStartDate().isAfter(LocalDate.now());
        membership.setStatus(isQueued ? Membership.Status.QUEUED : Membership.Status.ACTIVE);
        membership = membershipRepository.save(membership);

        // 4. Publish Kafka event → notification-service sends WhatsApp + email
        UserProfileDto user = fetchUserProfile(userId);
        AppSettings settings = appSettingsRepository.findById(1L).orElse(null);
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .userId(userId)
                .membershipId(membership.getId().toString())
                .userName(user.getName())
                .userMobile(user.getMobile())
                .userEmail(user.getEmail())
                .planName(membership.getPlan().getName())
                .planType(membership.getPlan().getPlanType().name())
                .seatNumber(membership.getSeatNumber())
                .shift(membership.getShift())
                .startDate(membership.getStartDate().toString())
                .endDate(membership.getEndDate().toString())
                .amountPaid(payment.getAmount())
                .wifiName(settings != null ? settings.getWifiName() : null)
                .wifiPassword(settings != null ? settings.getWifiPassword() : null)
                .eventType("BOOKING_CONFIRMED")
                .build();

        kafkaTemplate.send("booking-confirmed", userId, event);
        log.info("Published booking-confirmed Kafka event for user: {} membership: {}",
                userId, membership.getId());

        return MembershipDto.fromEntity(membership);
    }

    // Gateway-specific signature/status verification, shared by the normal
    // plan-purchase flow and the dues-payment flow below. Skipped in dev mode.
    private void verifyGatewayPayment(PaymentVerifyRequest request) {
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
    }

    // ── Pay Dues (clear a GRACE membership's outstanding amount) ───────────────
    // The seat was never released — paying dues just extends the SAME membership
    // row's endDate and resumes it, rather than creating a new membership.

    @Transactional
    public CreateOrderResponse createDuesOrder(String userId) {
        Membership membership = membershipRepository.findGraceByUserId(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No overdue membership found — nothing to pay."));

        BigDecimal duesAmount = membership.getDuesAmount();
        if (duesAmount == null || duesAmount.signum() <= 0) {
            throw new IllegalArgumentException("No dues outstanding on this membership.");
        }

        String gatewayOrderId;
        String paymentSessionId = null;

        if ("RAZORPAY".equals(activeGateway)) {
            gatewayOrderId = createRazorpayOrder(duesAmount, membership);
        } else {
            String[] cashfreeResult = createCashfreeOrder(duesAmount, membership, userId);
            gatewayOrderId   = cashfreeResult[0];
            paymentSessionId = cashfreeResult[1];
        }

        Payment payment = Payment.builder()
                .membershipId(membership.getId())
                .userId(UUID.fromString(userId))
                .amount(duesAmount)
                .paymentGateway(activeGateway)
                .gatewayOrderId(gatewayOrderId)
                .status(Payment.Status.PENDING)
                .build();
        paymentRepository.save(payment);

        return CreateOrderResponse.builder()
                .orderId(gatewayOrderId)
                .membershipId(membership.getId().toString())
                .amount(duesAmount)
                .currency("INR")
                .gateway(activeGateway)
                .paymentSessionId(paymentSessionId)
                .razorpayKeyId("RAZORPAY".equals(activeGateway) ? razorpayKeyId : null)
                .build();
    }

    @Transactional
    public MembershipDto verifyAndPayDues(String userId, PaymentVerifyRequest request) {
        verifyGatewayPayment(request);

        Payment payment = paymentRepository
                .findByGatewayOrderId(request.getGatewayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found"));

        payment.setGatewayPaymentId(request.getGatewayPaymentId());
        payment.setStatus(Payment.Status.SUCCESS);
        paymentRepository.save(payment);

        Membership membership = membershipRepository
                .findById(payment.getMembershipId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        // Defend against a race with an admin releasing this same seat in the meantime.
        if (membership.getStatus() != Membership.Status.GRACE) {
            throw new IllegalArgumentException(
                    "This membership is no longer awaiting dues — it may already have been paid or released.");
        }

        membership.setEndDate(membership.getEndDate().plusDays(membership.getPlan().getDurationDays()));
        membership.setStatus(Membership.Status.ACTIVE);
        membership.setDuesAmount(BigDecimal.ZERO);
        membership = membershipRepository.save(membership);

        log.info("Dues cleared for user {} — membership {} resumed, new endDate {}",
                userId, membership.getId(), membership.getEndDate());

        return MembershipDto.fromEntity(membership);
    }

    // ── Razorpay Helpers ──────────────────────────────────────────────────────

    private String createRazorpayOrder(BigDecimal amount, Membership membership) {
        if (!razorpayKeyId.isBlank()) {
            try {
                RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

                JSONObject options = new JSONObject();
                options.put("amount",
                        amount.multiply(BigDecimal.valueOf(100)).intValue());
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
    private String[] createCashfreeOrder(BigDecimal amount, Membership membership, String userId) {
        if (!cashfreeAppId.isBlank()) {
            try {
                UserProfileDto user = fetchUserProfile(userId);

                Map<String, Object> customerDetails = new HashMap<>();
                customerDetails.put("customer_id",    userId);
                customerDetails.put("customer_name",  user.getName()   != null && !user.getName().isBlank()   ? user.getName()   : "Library Student");
                customerDetails.put("customer_phone", user.getMobile() != null && !user.getMobile().isBlank() ? user.getMobile() : "9999999999");
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    customerDetails.put("customer_email", user.getEmail());
                }

                Map<String, Object> body = Map.of(
                        "order_id",         "lib_" + membership.getId().toString().substring(0, 8),
                        "order_amount",     amount,
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

                return new String[]{ resp.getOrderId(), resp.getPaymentSessionId() };

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
        headers.set("x-api-version", "2025-01-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String cashfreeBaseUrl() {
        return "production".equals(cashfreeEnv.trim())
                ? "https://api.cashfree.com"
                : "https://sandbox.cashfree.com";
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
            log.error("Could not fetch user profile (userId={}) — booking notification will use fallback name: {}",
                    userId, e.getMessage(), e);
        }
        return new UserProfileDto();
    }
}
