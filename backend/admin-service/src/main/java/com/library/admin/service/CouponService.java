package com.library.admin.service;

import com.library.admin.dto.CouponDto;
import com.library.admin.dto.CreateCouponRequest;
import com.library.admin.dto.UpdateCouponRequest;
import com.library.admin.entity.Coupon;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    // Excludes visually-ambiguous characters (0/O, 1/I/L) so a printed/spoken
    // code is unambiguous.
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int    CODE_LEN = 8;
    private static final int    MAX_GENERATE_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CouponRepository couponRepository;

    public List<CouponDto> listCoupons() {
        return couponRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CouponDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponDto createCoupon(CreateCouponRequest req) {
        String code;
        if (req.getCode() != null && !req.getCode().isBlank()) {
            code = req.getCode().trim().toUpperCase();
            if (couponRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("Coupon code " + code + " already exists");
            }
        } else {
            code = generateUniqueCode();
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercent(req.getDiscountPercent())
                .isActive(true)
                .build();
        return CouponDto.fromEntity(couponRepository.save(coupon));
    }

    @Transactional
    public CouponDto updateCoupon(UUID id, UpdateCouponRequest req) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));

        if (req.getDiscountPercent() != null) {
            coupon.setDiscountPercent(req.getDiscountPercent());
        }
        if (req.getIsActive() != null) {
            coupon.setActive(req.getIsActive());
        }
        return CouponDto.fromEntity(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteCoupon(UUID id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coupon not found: " + id);
        }
        couponRepository.deleteById(id);
    }

    // A pre-check-then-insert (rather than insert-and-retry-on-violation) is
    // fine here: this is a low-frequency admin action, not a hot path, and
    // avoids a Hibernate persistence-context left poisoned by a caught
    // DataIntegrityViolationException mid-transaction.
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!couponRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique coupon code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
