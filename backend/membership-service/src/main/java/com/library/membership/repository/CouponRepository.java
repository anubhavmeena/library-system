package com.library.membership.repository;

import com.library.membership.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, java.util.UUID> {
    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);
    List<Coupon> findAllByIsActiveTrue();
}
