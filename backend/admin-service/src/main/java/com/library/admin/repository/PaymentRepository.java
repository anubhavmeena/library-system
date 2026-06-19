package com.library.admin.repository;

import com.library.admin.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByMembershipId(UUID membershipId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Payment p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    // Total revenue for a time window — used by getDashboardStats and getRevenueReport
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = 'SUCCESS'
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    BigDecimal sumRevenueForPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

    // Transaction count for a time window — used by dashboard paymentsThisMonth
    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status = 'SUCCESS'
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    long countSuccessfulPayments(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );
}