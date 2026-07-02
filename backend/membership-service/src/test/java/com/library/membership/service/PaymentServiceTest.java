package com.library.membership.service;

import com.library.membership.dto.CreateOrderRequest;
import com.library.membership.dto.CreateOrderResponse;
import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.PaymentVerifyRequest;
import com.library.membership.dto.UserApiResponse;
import com.library.membership.dto.UserProfileDto;
import com.library.membership.entity.Membership;
import com.library.membership.entity.Payment;
import com.library.membership.entity.Plan;
import com.library.membership.event.BookingConfirmedEvent;
import com.library.membership.event.PaymentReceiptEvent;
import com.library.membership.exception.ResourceNotFoundException;
import com.library.membership.repository.AppSettingsRepository;
import com.library.membership.repository.MembershipRepository;
import com.library.membership.repository.PaymentRepository;
import com.library.membership.repository.PlanRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock MembershipRepository membershipRepository;
    @Mock PaymentRepository    paymentRepository;
    @Mock PlanRepository       planRepository;
    @Mock AppSettingsRepository appSettingsRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks PaymentService paymentService;

    private final String userId = UUID.randomUUID().toString();
    private static final String TEST_SECRET = "test-razorpay-secret-key";

    @BeforeEach
    void setUp() {
        // Default: Cashfree dev mode (all credentials blank)
        ReflectionTestUtils.setField(paymentService, "activeGateway",       "CASHFREE");
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId",       "");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret",   "");
        ReflectionTestUtils.setField(paymentService, "cashfreeAppId",       "");
        ReflectionTestUtils.setField(paymentService, "cashfreeSecretKey",   "");
        ReflectionTestUtils.setField(paymentService, "cashfreeEnv",         "sandbox");
        ReflectionTestUtils.setField(paymentService, "userServiceBaseUrl",  "http://localhost:8082");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Plan buildHalfDayPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Half Day Plan")
                .planType(Plan.PlanType.HALF_DAY)
                .price(BigDecimal.valueOf(400))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    private Plan buildFullDayPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Full Day Plan")
                .planType(Plan.PlanType.FULL_DAY)
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    private Membership buildSavedMembership(UUID id, Plan plan) {
        return Membership.builder()
                .id(id)
                .userId(UUID.fromString(userId))
                .plan(plan)
                .seatNumber("A1")
                .shift("FULL_DAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(Membership.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Payment buildSavedPayment(UUID membershipId) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .membershipId(membershipId)
                .userId(UUID.fromString(userId))
                .amount(BigDecimal.valueOf(600))
                .gatewayOrderId("dev_order_abc123")
                .status(Payment.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String computeHmac(String orderId, String paymentId, String secret) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }

    // ── createOrder — validation ───────────────────────────────────────────────

    @Test
    void createOrder_planNotFound_throwsResourceNotFoundException() {
        when(planRepository.findById(any())).thenReturn(Optional.empty());

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(UUID.randomUUID().toString());

        assertThatThrownBy(() -> paymentService.createOrder(userId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Plan not found");
    }

    @Test
    void createOrder_halfDayPlanNullShift_throwsIllegalArgument() {
        Plan plan = buildHalfDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setShift(null);

        assertThatThrownBy(() -> paymentService.createOrder(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shift selection");
    }

    @Test
    void createOrder_halfDayPlanFullDayShift_throwsIllegalArgument() {
        Plan plan = buildHalfDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setShift("FULL_DAY");

        assertThatThrownBy(() -> paymentService.createOrder(userId, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── createOrder — HALF_DAY plan dev mode ──────────────────────────────────

    @Test
    void createOrder_halfDayMorningShift_succeeds() {
        Plan plan = buildHalfDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        UUID memId = UUID.randomUUID();
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(memId, plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setShift("MORNING");

        CreateOrderResponse resp = paymentService.createOrder(userId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getOrderId()).startsWith("dev_order_");
    }

    @Test
    void createOrder_halfDayEveningShift_succeeds() {
        Plan plan = buildHalfDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setShift("EVENING");

        assertThatCode(() -> paymentService.createOrder(userId, req)).doesNotThrowAnyException();
    }

    // ── createOrder — FULL_DAY plan dev mode ──────────────────────────────────

    @Test
    void createOrder_fullDayPlan_shiftForcedToFullDay() {
        Plan plan = buildFullDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setShift("MORNING"); // ignored for FULL_DAY

        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getShift()).isEqualTo("FULL_DAY");
    }

    @Test
    void createOrder_devMode_orderIdStartsWithDevPrefix() {
        Plan plan = buildFullDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());

        CreateOrderResponse resp = paymentService.createOrder(userId, req);

        assertThat(resp.getOrderId()).startsWith("dev_order_");
    }

    @Test
    void createOrder_seatIdNull_membershipSeatIdNull() {
        Plan plan = buildFullDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setSeatId(null);

        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getSeatId()).isNull();
    }

    @Test
    void createOrder_seatIdProvided_membershipSeatIdSet() {
        Plan plan = buildFullDayPlan();
        UUID seatId = UUID.randomUUID();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        req.setSeatId(seatId.toString());
        req.setSeatNumber("A12");

        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getSeatId()).isEqualTo(seatId);
        assertThat(cap.getValue().getSeatNumber()).isEqualTo("A12");
    }

    @Test
    void createOrder_membershipStartDateTodayEndDateTodayPlusDuration() {
        Plan plan = buildFullDayPlan(); // durationDays = 30
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStartDate()).isEqualTo(LocalDate.now());
        assertThat(cap.getValue().getEndDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void createOrder_membershipStatusIsPending() {
        Plan plan = buildFullDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Membership.Status.PENDING);
    }

    @Test
    void createOrder_paymentSavedWithPendingStatusAndOrderId() {
        Plan plan = buildFullDayPlan();
        UUID memId = UUID.randomUUID();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(memId, plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        paymentService.createOrder(userId, req);

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(cap.getValue().getMembershipId()).isEqualTo(memId);
        assertThat(cap.getValue().getGatewayOrderId()).startsWith("dev_order_");
    }

    @Test
    void createOrder_responseContainsAmountAndCurrencyINR() {
        Plan plan = buildFullDayPlan(); // price = 600
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        CreateOrderResponse resp = paymentService.createOrder(userId, req);

        assertThat(resp.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(resp.getCurrency()).isEqualTo("INR");
    }

    // ── createOrder — Razorpay configured path ────────────────────────────────

    @Test
    void createOrder_razorpayException_throwsRuntimeException() throws Exception {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "rzp_test_secret");

        Plan plan = buildFullDayPlan();
        UUID memId = UUID.randomUUID();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(memId, plan));

        try (MockedConstruction<RazorpayClient> mocked = mockConstruction(RazorpayClient.class,
                (razorpayMock, ctx) -> {
                    OrderClient mockOrders = mock(OrderClient.class);
                    when(mockOrders.create(any())).thenThrow(new RuntimeException("Network error"));
                    Field f = RazorpayClient.class.getDeclaredField("orders");
                    f.setAccessible(true);
                    f.set(razorpayMock, mockOrders);
                })) {

            CreateOrderRequest req = new CreateOrderRequest();
            req.setPlanId(plan.getId().toString());

            assertThatThrownBy(() -> paymentService.createOrder(userId, req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Payment gateway error");
        }
    }

    @Test
    void createOrder_razorpaySuccess_orderIdFromGateway() throws Exception {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "rzp_test_secret");

        Plan plan = buildFullDayPlan();
        UUID memId = UUID.randomUUID();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(memId, plan));

        try (MockedConstruction<RazorpayClient> mocked = mockConstruction(RazorpayClient.class,
                (razorpayMock, ctx) -> {
                    OrderClient mockOrders = mock(OrderClient.class);
                    Order mockOrder = mock(Order.class);
                    doReturn("order_gateway123").when(mockOrder).get("id");
                    when(mockOrders.create(any())).thenReturn(mockOrder);
                    Field f = RazorpayClient.class.getDeclaredField("orders");
                    f.setAccessible(true);
                    f.set(razorpayMock, mockOrders);
                })) {

            CreateOrderRequest req = new CreateOrderRequest();
            req.setPlanId(plan.getId().toString());
            CreateOrderResponse resp = paymentService.createOrder(userId, req);

            assertThat(resp.getOrderId()).isEqualTo("order_gateway123");
        }
    }

    // ── verifyAndActivateMembership — HMAC bypass conditions ──────────────────

    @Test
    void verify_blankSecret_skipsHmacAndActivates() {
        // razorpayKeySecret is blank (set in @BeforeEach)
        String orderId = "order_test";
        String paymentId = "pay_test";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStatus(Membership.Status.PENDING);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId(paymentId);
        req.setSignature("any-signature-not-checked");

        assertThatCode(() -> paymentService.verifyAndActivateMembership(userId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_devOrderPrefix_skipsHmacEvenWithSecretConfigured() {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", TEST_SECRET);

        String devOrderId = "dev_order_abc123";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(devOrderId);
        when(paymentRepository.findByGatewayOrderId(devOrderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(devOrderId);
        req.setGatewayPaymentId("pay_test");
        req.setSignature("wrong-signature-should-be-ignored");

        assertThatCode(() -> paymentService.verifyAndActivateMembership(userId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_nullSignature_skipsHmac() {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", TEST_SECRET);

        String orderId = "order_real123";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_test");
        req.setSignature(null);

        assertThatCode(() -> paymentService.verifyAndActivateMembership(userId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_validHmacSignature_activates() throws Exception {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", TEST_SECRET);

        String orderId = "order_real456";
        String paymentId = "pay_real456";
        String validSig = computeHmac(orderId, paymentId, TEST_SECRET);
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId(paymentId);
        req.setSignature(validSig);

        assertThatCode(() -> paymentService.verifyAndActivateMembership(userId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_invalidHmacSignature_throwsIllegalArgument() {
        ReflectionTestUtils.setField(paymentService, "activeGateway", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", TEST_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("order_real789");
        req.setGatewayPaymentId("pay_real789");
        req.setSignature("tampered-signature");

        assertThatThrownBy(() -> paymentService.verifyAndActivateMembership(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    // ── verifyAndActivateMembership — not found paths ────────────────────────

    @Test
    void verify_paymentNotFound_throwsResourceNotFoundException() {
        when(paymentRepository.findByGatewayOrderId(any())).thenReturn(Optional.empty());

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("dev_order_missing");
        req.setGatewayPaymentId("pay_x");

        assertThatThrownBy(() -> paymentService.verifyAndActivateMembership(userId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment record not found");
    }

    @Test
    void verify_membershipNotFound_throwsResourceNotFoundException() {
        UUID memId = UUID.randomUUID();
        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId("dev_order_1");
        when(paymentRepository.findByGatewayOrderId("dev_order_1")).thenReturn(Optional.of(payment));
        when(membershipRepository.findById(memId)).thenReturn(Optional.empty());

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("dev_order_1");
        req.setGatewayPaymentId("pay_x");

        assertThatThrownBy(() -> paymentService.verifyAndActivateMembership(userId, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership not found");
    }

    // ── verifyAndActivateMembership — activation side effects ─────────────────

    @Test
    void verify_success_paymentStatusSetToSuccess() {
        String orderId = "dev_order_2";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_verified");

        paymentService.verifyAndActivateMembership(userId, req);

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Payment.Status.SUCCESS);
        assertThat(cap.getValue().getGatewayPaymentId()).isEqualTo("pay_verified");
    }

    @Test
    void verify_success_membershipStatusSetToActive() {
        String orderId = "dev_order_3";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStatus(Membership.Status.PENDING);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_act");

        paymentService.verifyAndActivateMembership(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Membership.Status.ACTIVE);
    }

    @Test
    void verify_success_kafkaEventPublishedToBookingConfirmedTopic() {
        String orderId = "dev_order_4";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_kafka");

        paymentService.verifyAndActivateMembership(userId, req);

        verify(kafkaTemplate).send(eq("booking-confirmed"), eq(userId), any(BookingConfirmedEvent.class));
    }

    @Test
    void verify_success_kafkaEventFieldsCorrect() {
        String orderId = "dev_order_5";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(600));
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setShift("FULL_DAY");
        membership.setSeatNumber("B12");
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        UserProfileDto userProfile = new UserProfileDto();
        userProfile.setName("Ravi Kumar");
        userProfile.setMobile("9876543210");
        userProfile.setEmail("ravi@example.com");
        UserApiResponse userApiResponse = new UserApiResponse();
        userApiResponse.setData(userProfile);
        when(restTemplate.exchange(anyString(), any(), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(userApiResponse));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_fields");

        paymentService.verifyAndActivateMembership(userId, req);

        ArgumentCaptor<BookingConfirmedEvent> cap = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(kafkaTemplate).send(eq("booking-confirmed"), anyString(), cap.capture());

        BookingConfirmedEvent event = cap.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getMembershipId()).isEqualTo(memId.toString());
        assertThat(event.getUserName()).isEqualTo("Ravi Kumar");
        assertThat(event.getUserMobile()).isEqualTo("9876543210");
        assertThat(event.getUserEmail()).isEqualTo("ravi@example.com");
        assertThat(event.getPlanName()).isEqualTo("Full Day Plan");
        assertThat(event.getPlanType()).isEqualTo("FULL_DAY");
        assertThat(event.getSeatNumber()).isEqualTo("B12");
        assertThat(event.getShift()).isEqualTo("FULL_DAY");
        assertThat(event.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(event.getEventType()).isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    void verify_success_publishesPaymentReceiptEvent() {
        String orderId = "dev_order_receipt";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(600));
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setSeatNumber("B12");
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        UserProfileDto userProfile = new UserProfileDto();
        userProfile.setName("Ravi Kumar");
        userProfile.setMobile("9876543210");
        userProfile.setEmail("ravi@example.com");
        UserApiResponse userApiResponse = new UserApiResponse();
        userApiResponse.setData(userProfile);
        when(restTemplate.exchange(anyString(), any(), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(userApiResponse));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_receipt");

        paymentService.verifyAndActivateMembership(userId, req);

        ArgumentCaptor<PaymentReceiptEvent> cap = ArgumentCaptor.forClass(PaymentReceiptEvent.class);
        verify(kafkaTemplate).send(eq("payment-receipt"), eq(userId), cap.capture());

        PaymentReceiptEvent event = cap.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getInvoiceId()).startsWith("INV-");
        assertThat(event.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(event.getAmountPending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(event.getReceiptType()).isEqualTo("NEW_BOOKING");
        assertThat(payment.getInvoiceId()).isEqualTo(event.getInvoiceId());
    }

    // ── verifyAndPayDues ───────────────────────────────────────────────────────

    @Test
    void verifyAndPayDues_success_resumesGraceMembershipAndPublishesReceipt() {
        String orderId = "dev_order_dues";
        UUID memId = UUID.randomUUID();

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStatus(Membership.Status.GRACE);
        membership.setDuesAmount(BigDecimal.valueOf(600));
        membership.setEndDate(LocalDate.now().minusDays(5));
        LocalDate originalEndDate = membership.getEndDate();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(600));
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDto userProfile = new UserProfileDto();
        userProfile.setName("Ravi Kumar");
        userProfile.setMobile("9876543210");
        userProfile.setEmail("ravi@example.com");
        UserApiResponse userApiResponse = new UserApiResponse();
        userApiResponse.setData(userProfile);
        when(restTemplate.exchange(anyString(), any(), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(userApiResponse));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_dues");

        MembershipDto result = paymentService.verifyAndPayDues(userId, req);

        assertThat(result.getStatus()).isEqualTo(Membership.Status.ACTIVE.name());
        assertThat(membership.getDuesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(membership.getEndDate()).isEqualTo(originalEndDate.plusDays(plan.getDurationDays()));
        assertThat(payment.getInvoiceId()).startsWith("INV-");

        ArgumentCaptor<PaymentReceiptEvent> cap = ArgumentCaptor.forClass(PaymentReceiptEvent.class);
        verify(kafkaTemplate).send(eq("payment-receipt"), eq(userId), cap.capture());
        PaymentReceiptEvent event = cap.getValue();
        assertThat(event.getReceiptType()).isEqualTo("DUES_CLEARED");
        assertThat(event.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(event.getAmountPending()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void verifyAndPayDues_membershipNotGrace_throwsIllegalArgument() {
        String orderId = "dev_order_dues2";
        UUID memId = UUID.randomUUID();

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStatus(Membership.Status.ACTIVE);

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_dues2");

        assertThatThrownBy(() -> paymentService.verifyAndPayDues(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer awaiting dues");
    }

    @Test
    void verify_success_returnsMembershipDtoWithActiveStatus() {
        String orderId = "dev_order_6";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStatus(Membership.Status.ACTIVE);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_dto");

        MembershipDto result = paymentService.verifyAndActivateMembership(userId, req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getId()).isEqualTo(memId.toString());
    }

    // ── createOrder — queued plan logic ──────────────────────────────────────

    @Test
    void createOrder_noActiveMembership_startDateIsToday() {
        Plan plan = buildFullDayPlan();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStartDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void createOrder_activeMembershipExists_startDateIsActivePlusOne() {
        // Use a HALF_DAY plan so the active membership's shift ("MORNING") is inherited
        Plan plan = buildHalfDayPlan();
        UUID activeSeatId = UUID.randomUUID();
        Membership active = Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .plan(plan)
                .seatId(activeSeatId)
                .seatNumber("C5")
                .shift("MORNING")
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().plusDays(10))
                .status(Membership.Status.ACTIVE)
                .build();

        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.of(active));
        when(membershipRepository.findQueuedByUserId(UUID.fromString(userId))).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenReturn(buildSavedMembership(UUID.randomUUID(), plan));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());
        // No shift set — should be inherited from the active membership
        paymentService.createOrder(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStartDate()).isEqualTo(LocalDate.now().plusDays(11));
        assertThat(cap.getValue().getSeatId()).isEqualTo(activeSeatId);
        assertThat(cap.getValue().getSeatNumber()).isEqualTo("C5");
        assertThat(cap.getValue().getShift()).isEqualTo("MORNING");
    }

    @Test
    void createOrder_activeMembership_alreadyQueued_throwsIllegalArgument() {
        Plan plan = buildFullDayPlan();
        Membership active = Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .plan(plan)
                .endDate(LocalDate.now().plusDays(10))
                .status(Membership.Status.ACTIVE)
                .build();
        Membership queued = Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .plan(plan)
                .startDate(LocalDate.now().plusDays(11))
                .status(Membership.Status.QUEUED)
                .build();

        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(membershipRepository.findActiveByUserId(UUID.fromString(userId))).thenReturn(Optional.of(active));
        when(membershipRepository.findQueuedByUserId(UUID.fromString(userId))).thenReturn(Optional.of(queued));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(plan.getId().toString());

        assertThatThrownBy(() -> paymentService.createOrder(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already have a plan queued");
    }

    // ── verifyAndActivateMembership — QUEUED status when startDate is future ──

    @Test
    void verify_startDateFuture_setsQueuedStatus() {
        String orderId = "dev_order_queue1";
        UUID memId = UUID.randomUUID();

        Payment payment = buildSavedPayment(memId);
        payment.setGatewayOrderId(orderId);
        when(paymentRepository.findByGatewayOrderId(orderId)).thenReturn(Optional.of(payment));

        Plan plan = buildFullDayPlan();
        Membership membership = buildSavedMembership(memId, plan);
        membership.setStartDate(LocalDate.now().plusDays(1));
        membership.setStatus(Membership.Status.PENDING);
        when(membershipRepository.findById(memId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any())).thenReturn(membership);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId(orderId);
        req.setGatewayPaymentId("pay_queue");

        paymentService.verifyAndActivateMembership(userId, req);

        ArgumentCaptor<Membership> cap = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Membership.Status.QUEUED);
    }
}
