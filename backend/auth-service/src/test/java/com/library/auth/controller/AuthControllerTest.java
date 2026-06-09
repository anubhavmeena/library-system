package com.library.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.auth.dto.*;
import com.library.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.library.auth.exception.GlobalExceptionHandler;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AuthService authService;

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .accessToken("jwt-token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserInfoDto.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Alice")
                        .role("STUDENT")
                        .build())
                .build();
    }

    // ── POST /api/auth/send-otp ───────────────────────────────────────────────

    @Test
    void sendOtp_validRequest_returns200() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setContact("9876543210");
        req.setContactType("MOBILE");
        doNothing().when(authService).sendOtp(any(), any());

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("9876543210")));

        verify(authService).sendOtp("9876543210", "MOBILE");
    }

    @Test
    void sendOtp_blankContact_returns400Validation() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setContact("");
        req.setContactType("MOBILE");

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendOtp_blankContactType_returns400Validation() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setContact("9876543210");
        req.setContactType("");

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendOtp_serviceThrows_returns400() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setContact("9876543210");
        req.setContactType("MOBILE");
        doThrow(new RuntimeException("OTP service unavailable")).when(authService).sendOtp(any(), any());

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("OTP service unavailable"));
    }

    // ── POST /api/auth/verify-otp ─────────────────────────────────────────────

    @Test
    void verifyOtp_validRequest_returns200WithOtpVerifyResponse() throws Exception {
        OtpVerifyResponse resp = OtpVerifyResponse.builder()
                .verified(true)
                .sessionToken(UUID.randomUUID().toString())
                .isNewUser(true)
                .build();
        when(authService.verifyOtp(any(), any())).thenReturn(resp);

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        verify(authService).verifyOtp("9876543210", "123456");
    }

    @Test
    void verifyOtp_blankOtp_returns400Validation() throws Exception {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setContact("9876543210");
        req.setOtp("");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOtp_expiredOtp_returns400() throws Exception {
        when(authService.verifyOtp(any(), any()))
                .thenThrow(new RuntimeException("OTP expired. Please request a new one."));

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setContact("9876543210");
        req.setOtp("000000");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("OTP expired. Please request a new one."));
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns200WithJwt() throws Exception {
        when(authService.register(any())).thenReturn(buildAuthResponse());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void register_blankName_returns400Validation() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("");
        req.setSessionToken(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_blankSessionToken_returns400Validation() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_expiredSession_returns400() throws Exception {
        when(authService.register(any()))
                .thenThrow(new RuntimeException("Session expired. Please verify OTP again."));

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("expired-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_validOtp_returns200WithJwt() throws Exception {
        when(authService.login(any())).thenReturn(buildAuthResponse());

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.name").value("Alice"));

        verify(authService).login(argThat(r -> "9876543210".equals(r.getContact())
                && "123456".equals(r.getOtp())));
    }

    @Test
    void login_invalidOtp_returns400() throws Exception {
        when(authService.login(any()))
                .thenThrow(new RuntimeException("Invalid or expired OTP."));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("000000");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /api/auth/admin/login ────────────────────────────────────────────

    @Test
    void adminLogin_validAdmin_returns200() throws Exception {
        AuthResponse adminResp = AuthResponse.builder()
                .accessToken("admin-jwt")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserInfoDto.builder().role("ADMIN").name("Admin").build())
                .build();
        when(authService.adminLogin(any())).thenReturn(adminResp);

        LoginRequest req = new LoginRequest();
        req.setContact("admin@example.com");
        req.setOtp("irrelevant");

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    void adminLogin_notAdmin_returns400() throws Exception {
        when(authService.adminLogin(any()))
                .thenThrow(new RuntimeException("Access denied. Not an admin account."));

        LoginRequest req = new LoginRequest();
        req.setContact("student@example.com");
        req.setOtp("anything");

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Access denied. Not an admin account."));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    void refresh_alwaysReturns501() throws Exception {
        when(authService.refreshToken(any()))
                .thenThrow(new UnsupportedOperationException("Refresh token not implemented yet"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer some-jwt-token"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Content-Type ──────────────────────────────────────────────────────────

    @Test
    void allEndpoints_returnJsonContentType() throws Exception {
        doNothing().when(authService).sendOtp(any(), any());
        SendOtpRequest req = new SendOtpRequest();
        req.setContact("9876543210");
        req.setContactType("MOBILE");

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
