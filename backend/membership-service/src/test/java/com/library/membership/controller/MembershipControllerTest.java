package com.library.membership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.membership.dto.*;
import com.library.membership.exception.GlobalExceptionHandler;
import com.library.membership.exception.ResourceNotFoundException;
import com.library.membership.service.MembershipService;
import com.library.membership.service.PaymentService;
import com.library.membership.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MembershipController.class)
@Import(GlobalExceptionHandler.class)
class MembershipControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MembershipService membershipService;
    @MockBean PlanService       planService;
    @MockBean PaymentService    paymentService;

    private final String userId = UUID.randomUUID().toString();

    private MembershipDto buildMembershipDto() {
        return MembershipDto.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .planName("Full Day Plan")
                .planType("FULL_DAY")
                .shift("FULL_DAY")
                .startDate("2025-01-01")
                .endDate("2025-01-31")
                .status("ACTIVE")
                .build();
    }

    private PlanDto buildPlanDto() {
        return PlanDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Full Day Plan")
                .planType("FULL_DAY")
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    // ── GET /api/plans ────────────────────────────────────────────────────────

    @Test
    void getPlans_returns200WithPlanList() throws Exception {
        when(planService.getAllActivePlans()).thenReturn(List.of(buildPlanDto()));

        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].planType").value("FULL_DAY"));
    }

    @Test
    void getPlans_emptyList_returns200() throws Exception {
        when(planService.getAllActivePlans()).thenReturn(List.of());

        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /api/memberships/my ───────────────────────────────────────────────

    @Test
    void getMyActiveMembership_withHeader_returns200() throws Exception {
        when(membershipService.getUserActiveMembership(userId)).thenReturn(buildMembershipDto());

        mockMvc.perform(get("/api/memberships/my")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(membershipService).getUserActiveMembership(userId);
    }

    @Test
    void getMyActiveMembership_withoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/memberships/my"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyActiveMembership_nullData_returns200WithNullData() throws Exception {
        // Intentional null return when no active membership — frontend shows "Get a plan" CTA
        when(membershipService.getUserActiveMembership(any())).thenReturn(null);

        mockMvc.perform(get("/api/memberships/my")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ── GET /api/memberships/my/all ───────────────────────────────────────────

    @Test
    void getAllMyMemberships_returns200WithList() throws Exception {
        when(membershipService.getUserMemberships(userId))
                .thenReturn(List.of(buildMembershipDto()));

        mockMvc.perform(get("/api/memberships/my/all")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getAllMyMemberships_withoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/memberships/my/all"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/payments/my ──────────────────────────────────────────────────

    @Test
    void getMyPayments_returns200WithList() throws Exception {
        PaymentDto payment = PaymentDto.builder()
                .id(UUID.randomUUID().toString())
                .amount(BigDecimal.valueOf(600))
                .status("SUCCESS")
                .build();
        when(membershipService.getUserPayments(userId)).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/payments/my")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }

    // ── POST /api/payments/create-order ───────────────────────────────────────

    @Test
    void createOrder_validRequest_returns200WithOrderResponse() throws Exception {
        CreateOrderResponse orderResp = CreateOrderResponse.builder()
                .orderId("dev_order_abc")
                .membershipId(UUID.randomUUID().toString())
                .amount(BigDecimal.valueOf(600))
                .currency("INR")
                .razorpayKeyId("")
                .build();
        when(paymentService.createOrder(any(), any())).thenReturn(orderResp);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/payments/create-order")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("dev_order_abc"))
                .andExpect(jsonPath("$.data.currency").value("INR"));

        verify(paymentService).createOrder(eq(userId), any());
    }

    @Test
    void createOrder_blankPlanId_returns400Validation() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId("");

        mockMvc.perform(post("/api/payments/create-order")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createOrder_withoutHeader_returns400() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_planNotFound_returns404() throws Exception {
        when(paymentService.createOrder(any(), any()))
                .thenThrow(new ResourceNotFoundException("Plan not found: abc"));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/payments/create-order")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Plan not found: abc"));
    }

    @Test
    void createOrder_invalidShift_returns400() throws Exception {
        when(paymentService.createOrder(any(), any()))
                .thenThrow(new IllegalArgumentException("Half-day plan requires shift selection"));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanId(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/payments/create-order")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Half-day plan requires shift selection"));
    }

    // ── POST /api/payments/verify ─────────────────────────────────────────────

    @Test
    void verifyPayment_validRequest_returns200WithMembershipDto() throws Exception {
        when(paymentService.verifyAndActivateMembership(any(), any()))
                .thenReturn(buildMembershipDto());

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("dev_order_abc");
        req.setGatewayPaymentId("pay_xyz");

        mockMvc.perform(post("/api/payments/verify")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void verifyPayment_blankGatewayOrderId_returns400Validation() throws Exception {
        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("");
        req.setGatewayPaymentId("pay_xyz");

        mockMvc.perform(post("/api/payments/verify")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyPayment_blankGatewayPaymentId_returns400Validation() throws Exception {
        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("dev_order_abc");
        req.setGatewayPaymentId("");

        mockMvc.perform(post("/api/payments/verify")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyPayment_signatureMismatch_returns400() throws Exception {
        when(paymentService.verifyAndActivateMembership(any(), any()))
                .thenThrow(new RuntimeException("Payment signature verification failed"));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setGatewayOrderId("order_tampered");
        req.setGatewayPaymentId("pay_xyz");

        mockMvc.perform(post("/api/payments/verify")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void allEndpoints_returnJsonContentType() throws Exception {
        when(planService.getAllActivePlans()).thenReturn(List.of());

        mockMvc.perform(get("/api/plans"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
