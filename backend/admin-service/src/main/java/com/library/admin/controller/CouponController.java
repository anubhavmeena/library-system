package com.library.admin.controller;

import com.library.admin.dto.CouponDto;
import com.library.admin.dto.CreateCouponRequest;
import com.library.admin.dto.UpdateCouponRequest;
import com.library.admin.service.CouponService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CouponDto>>> listCoupons() {
        return ResponseEntity.ok(ApiResponse.success(couponService.listCoupons()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouponDto>> createCoupon(
            @Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.createCoupon(request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponDto>> updateCoupon(
            @PathVariable UUID id, @Valid @RequestBody UpdateCouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.updateCoupon(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable UUID id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
