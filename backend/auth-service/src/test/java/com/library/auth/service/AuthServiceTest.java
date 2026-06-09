package com.library.auth.service;

import com.library.auth.dto.*;
import com.library.auth.entity.User;
import com.library.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock OtpService otpService;
    @InjectMocks AuthService authService;

    private static final String JWT_SECRET =
            "test-jwt-secret-key-must-be-at-least-32-bytes-long-for-hmac256";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400000L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private User buildStudent() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .mobile("9876543210")
                .email("alice@example.com")
                .role(User.Role.STUDENT)
                .isActive(true)
                .build();
    }

    private User buildAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .mobile("9000000000")
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build();
    }

    // ── sendOtp ───────────────────────────────────────────────────────────────

    @Test
    void sendOtp_storesOtpInRedisWithFiveMinuteTtl() {
        authService.sendOtp("9876543210", "MOBILE");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), otpCaptor.capture(), eq(5L), eq(TimeUnit.MINUTES));

        assertThat(keyCaptor.getValue()).isEqualTo("otp:9876543210");
        assertThat(otpCaptor.getValue()).matches("[0-9]{6}");
    }

    @Test
    void sendOtp_sameOtpStoredAndSentToOtpService() {
        authService.sendOtp("9876543210", "MOBILE");

        ArgumentCaptor<String> storedOtpCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sentOtpCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("otp:9876543210"), storedOtpCaptor.capture(), anyLong(), any());
        verify(otpService).sendOtp(eq("9876543210"), eq("MOBILE"), sentOtpCaptor.capture());

        assertThat(storedOtpCaptor.getValue()).isEqualTo(sentOtpCaptor.getValue());
    }

    @Test
    void sendOtp_emailContact_usesEmailKey() {
        authService.sendOtp("test@example.com", "EMAIL");

        verify(valueOps).set(eq("otp:test@example.com"), any(), eq(5L), eq(TimeUnit.MINUTES));
        verify(otpService).sendOtp(eq("test@example.com"), eq("EMAIL"), any());
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_nullStoredOtp_throwsOtpExpired() {
        when(valueOps.get("otp:9876543210")).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyOtp("9876543210", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OTP expired");
    }

    @Test
    void verifyOtp_wrongOtp_throwsInvalidOtp() {
        when(valueOps.get("otp:9876543210")).thenReturn("654321");

        assertThatThrownBy(() -> authService.verifyOtp("9876543210", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid OTP");
    }

    @Test
    void verifyOtp_validOtp_deletesOtpKeyFromRedis() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);

        authService.verifyOtp("9876543210", "123456");

        verify(redisTemplate).delete("otp:9876543210");
    }

    @Test
    void verifyOtp_newUser_isNewUserTrue() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.existsByMobileOrEmail("9876543210", "9876543210")).thenReturn(false);

        OtpVerifyResponse response = authService.verifyOtp("9876543210", "123456");

        assertThat(response.isVerified()).isTrue();
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.getSessionToken()).isNotBlank();
    }

    @Test
    void verifyOtp_existingUser_isNewUserFalse() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.existsByMobileOrEmail("9876543210", "9876543210")).thenReturn(true);

        OtpVerifyResponse response = authService.verifyOtp("9876543210", "123456");

        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    void verifyOtp_storesSessionTokenWithFifteenMinuteTtl() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);

        OtpVerifyResponse response = authService.verifyOtp("9876543210", "123456");

        verify(valueOps).set(
                eq("session:" + response.getSessionToken()),
                eq("9876543210"),
                eq(15L),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void verifyOtp_sessionTokenIsUuidFormat() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);

        OtpVerifyResponse response = authService.verifyOtp("9876543210", "123456");

        assertThatCode(() -> UUID.fromString(response.getSessionToken()))
                .doesNotThrowAnyException();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_expiredSession_throws() {
        when(valueOps.get("session:bad-token")).thenReturn(null);

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("bad-token");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Session expired");
    }

    @Test
    void register_alreadyRegistered_throws() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail("9876543210", "9876543210")).thenReturn(true);

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void register_mobileContact_setsMobileField() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getMobile()).isEqualTo("9876543210");
        assertThat(cap.getValue().getEmail()).isNull();
    }

    @Test
    void register_emailContact_setsEmailField() {
        when(valueOps.get("session:tok")).thenReturn("alice@example.com");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(cap.getValue().getMobile()).isNull();
    }

    @Test
    void register_requestEmailOverridesContactEmail() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        req.setEmail("extra@example.com");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("extra@example.com");
    }

    @Test
    void register_blankRequestEmail_doesNotOverrideContactEmail() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        req.setEmail("   ");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isNull();
    }

    @Test
    void register_withDateOfBirth_parsesDate() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        req.setDateOfBirth("2000-05-15");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getDateOfBirth())
                .isEqualTo(java.time.LocalDate.of(2000, 5, 15));
    }

    @Test
    void register_nullDateOfBirth_notSet() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getDateOfBirth()).isNull();
    }

    @Test
    void register_setsRoleStudentAndIsActiveTrue() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        authService.register(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(cap.getValue().getIsActive()).isTrue();
    }

    @Test
    void register_deletesSessionTokenAfterSave() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        authService.register(req);

        verify(redisTemplate).delete("session:tok");
    }

    @Test
    void register_returnsAuthResponseWithBearerToken() {
        when(valueOps.get("session:tok")).thenReturn("9876543210");
        when(userRepository.existsByMobileOrEmail(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(buildStudent());

        RegisterRequest req = new RegisterRequest();
        req.setName("Alice");
        req.setSessionToken("tok");
        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUser()).isNotNull();
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_nullOtp_throwsInvalidOrExpired() {
        when(valueOps.get("otp:9876543210")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }

    @Test
    void login_wrongOtp_throwsInvalidOrExpired() {
        when(valueOps.get("otp:9876543210")).thenReturn("999999");

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }

    @Test
    void login_userNotFound_throws() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail("9876543210", "9876543210"))
                .thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void login_validOtp_deletesOtpKey() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail(any(), any()))
                .thenReturn(Optional.of(buildStudent()));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");
        authService.login(req);

        verify(redisTemplate).delete("otp:9876543210");
    }

    @Test
    void login_success_returnsAuthResponse() {
        User student = buildStudent();
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail("9876543210", "9876543210"))
                .thenReturn(Optional.of(student));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");
        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getId()).isEqualTo(student.getId().toString());
        assertThat(response.getUser().getName()).isEqualTo("Alice");
        assertThat(response.getUser().getRole()).isEqualTo("STUDENT");
    }

    @Test
    void login_expiresInIs86400() {
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail(any(), any()))
                .thenReturn(Optional.of(buildStudent()));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        assertThat(authService.login(req).getExpiresIn()).isEqualTo(86400L);
    }

    // ── adminLogin ────────────────────────────────────────────────────────────

    @Test
    void adminLogin_userNotFound_throws() {
        when(userRepository.findByMobileOrEmail(any(), any())).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setContact("admin@example.com");
        req.setOtp("anything");

        assertThatThrownBy(() -> authService.adminLogin(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Admin not found");
    }

    @Test
    void adminLogin_studentRole_throwsAccessDenied() {
        when(userRepository.findByMobileOrEmail(any(), any()))
                .thenReturn(Optional.of(buildStudent()));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("anything");

        assertThatThrownBy(() -> authService.adminLogin(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void adminLogin_adminRole_returnsJwt() {
        User admin = buildAdmin();
        when(userRepository.findByMobileOrEmail("admin@example.com", "admin@example.com"))
                .thenReturn(Optional.of(admin));

        LoginRequest req = new LoginRequest();
        req.setContact("admin@example.com");
        req.setOtp("ignored");

        AuthResponse response = authService.adminLogin(req);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getRole()).isEqualTo("ADMIN");
    }

    @Test
    void adminLogin_doesNotCheckRedisForOtp() {
        when(userRepository.findByMobileOrEmail(any(), any()))
                .thenReturn(Optional.of(buildAdmin()));

        LoginRequest req = new LoginRequest();
        req.setContact("admin@example.com");
        req.setOtp("irrelevant");
        authService.adminLogin(req);

        verify(valueOps, never()).get(any());
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_alwaysThrowsUnsupportedOperationException() {
        assertThatThrownBy(() -> authService.refreshToken("any-token"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Refresh token not implemented");
    }

    // ── JWT claims ────────────────────────────────────────────────────────────

    @Test
    void login_jwtContainsCorrectSubjectAndRoleClaims() {
        User student = buildStudent();
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail(any(), any()))
                .thenReturn(Optional.of(student));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");
        AuthResponse response = authService.login(req);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(response.getAccessToken())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(student.getId().toString());
        assertThat(claims.get("role")).isEqualTo("STUDENT");
        assertThat(claims.get("name")).isEqualTo("Alice");
        assertThat(claims.get("email")).isEqualTo("alice@example.com");
        assertThat(claims.get("mobile")).isEqualTo("9876543210");
    }

    @Test
    void adminLogin_jwtContainsAdminRole() {
        User admin = buildAdmin();
        when(userRepository.findByMobileOrEmail(any(), any())).thenReturn(Optional.of(admin));

        LoginRequest req = new LoginRequest();
        req.setContact("admin@example.com");
        req.setOtp("x");
        AuthResponse response = authService.adminLogin(req);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(response.getAccessToken())
                .getPayload();

        assertThat(claims.get("role")).isEqualTo("ADMIN");
        assertThat(claims.getSubject()).isEqualTo(admin.getId().toString());
    }

    @Test
    void login_userInfoDto_nullPhotoUrlPassedThrough() {
        User student = buildStudent(); // photoUrl is null
        when(valueOps.get("otp:9876543210")).thenReturn("123456");
        when(userRepository.findByMobileOrEmail(any(), any())).thenReturn(Optional.of(student));

        LoginRequest req = new LoginRequest();
        req.setContact("9876543210");
        req.setOtp("123456");

        assertThat(authService.login(req).getUser().getPhotoUrl()).isNull();
    }
}
