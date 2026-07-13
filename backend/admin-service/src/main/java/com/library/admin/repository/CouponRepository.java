package com.library.admin.repository;

import com.library.admin.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    List<Coupon> findAllByOrderByCreatedAtDesc();
    boolean existsByCodeIgnoreCase(String code);
}
