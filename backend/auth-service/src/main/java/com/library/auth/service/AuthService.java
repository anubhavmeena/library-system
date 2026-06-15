package com.library.auth.service;

import com.library.auth.dto.*;
import com.library.auth.entity.User;
import com.library.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final OtpService otpService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:86400000}")
    private Long jwtExpiration;

    // ── OTP Flow ──────────────────────────────────────────────────────────────

    public void sendOtp(String contact, String contactType) {
        String cooldownKey = "otp:cooldown:" + contact;
        Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            throw new IllegalArgumentException("Please wait " + ttl + " more seconds before requesting a new OTP.");
        }
        String otp = generateOtp();
        redisTemplate.opsForValue().set("otp:" + contact, otp, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(cooldownKey, "1", 30, TimeUnit.SECONDS);
        otpService.sendOtp(contact, contactType, otp);
        log.info("OTP sent to {} ({})", contact, contactType);
    }

    public OtpVerifyResponse verifyOtp(String contact, String otp) {
        String redisKey = "otp:" + contact;
        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            throw new RuntimeException("OTP expired. Please request a new one.");
        }
        if (!storedOtp.equals(otp)) {
            throw new RuntimeException("Invalid OTP. Please try again.");
        }

        redisTemplate.delete(redisKey);

        boolean isNewUser = !userRepository.existsByMobileOrEmail(contact, contact);

        String sessionToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("session:" + sessionToken, contact, 15, TimeUnit.MINUTES);

        return OtpVerifyResponse.builder()
                .verified(true)
                .sessionToken(sessionToken)
                .isNewUser(isNewUser)
                .build();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        String contact = redisTemplate.opsForValue().get("session:" + request.getSessionToken());
        if (contact == null) {
            throw new RuntimeException("Session expired. Please verify OTP again.");
        }
        if (userRepository.existsByMobileOrEmail(contact, contact)) {
            throw new RuntimeException("User already registered. Please login.");
        }

        User user = User.builder()
                .name(request.getName())
                .address(request.getAddress())
                .gender(request.getGender())
                .role(User.Role.STUDENT)
                .isActive(true)
                .build();

        if (contact.contains("@")) {
            user.setEmail(contact);
        } else {
            user.setMobile(contact);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        }

        User savedUser = userRepository.save(user);
        redisTemplate.delete("session:" + request.getSessionToken());

        return buildAuthResponse(generateJwt(savedUser), savedUser);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        String contact = redisTemplate.opsForValue().get("session:" + request.getSessionToken());
        if (contact == null) {
            throw new RuntimeException("Session expired. Please verify OTP again.");
        }
        redisTemplate.delete("session:" + request.getSessionToken());

        User user = userRepository
                .findByMobileOrEmail(contact, contact)
                .orElseThrow(() -> new RuntimeException("User not found. Please register first."));

        return buildAuthResponse(generateJwt(user), user);
    }

    public AuthResponse adminLogin(LoginRequest request) {
        User user = userRepository
                .findByMobileOrEmail(request.getContact(), request.getContact())
                .orElseThrow(() -> new RuntimeException("Admin not found."));

        if (user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Access denied. Not an admin account.");
        }
        return buildAuthResponse(generateJwt(user), user);
    }

    public AuthResponse refreshToken(String token) {
        throw new UnsupportedOperationException("Refresh token not implemented yet");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateJwt(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("role",   user.getRole().name());
        claims.put("name",   user.getName());
        claims.put("email",  user.getEmail());
        claims.put("mobile", user.getMobile());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }

    private AuthResponse buildAuthResponse(String token, User user) {
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .user(UserInfoDto.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .mobile(user.getMobile())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .photoUrl(user.getPhotoUrl())
                        .build())
                .build();
    }

    private String generateOtp() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if ("dev".equals(profile) && !otpService.isLiveConfigured()) return "123456";
        return String.format("%06d", new Random().nextInt(999999));
    }
}