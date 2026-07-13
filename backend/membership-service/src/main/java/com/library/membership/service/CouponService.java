package com.library.membership.service;

import com.library.membership.dto.CouponDto;
import com.library.membership.entity.AppSettings;
import com.library.membership.entity.Coupon;
import com.library.membership.repository.AppSettingsRepository;
import com.library.membership.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository      couponRepository;
    private final AppSettingsRepository appSettingsRepository;

    private boolean couponsGloballyEnabled() {
        return appSettingsRepository.findById(1L)
                .map(AppSettings::isCouponsEnabled)
                .orElse(false);
    }

    /** Student-facing list — empty whenever the global toggle is off. */
    public List<CouponDto> listActiveCoupons() {
        if (!couponsGloballyEnabled()) {
            return List.of();
        }
        return couponRepository.findAllByIsActiveTrue().stream()
                .map(CouponDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Validates a code for checkout — must be individually active and the
     * global toggle must be on. Throws IllegalArgumentException (→ 400)
     * otherwise, same as every other checkout validation in this service.
     */
    public Coupon validateCoupon(String code) {
        if (!couponsGloballyEnabled()) {
            throw new IllegalArgumentException("Coupons are not currently available");
        }
        return couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive coupon code"));
    }
}
