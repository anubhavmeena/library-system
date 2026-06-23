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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByMembershipId(UUID membershipId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

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

    // Successful payments grouped by amount for a period — used for pie chart breakdown
    @Query("""
        SELECT p.amount, COUNT(p)
        FROM Payment p
        WHERE p.status = 'SUCCESS'
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        GROUP BY p.amount
        ORDER BY COUNT(p) DESC
        """)
    List<Object[]> countByAmountForPeriod(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

    // Students with outstanding cash balance
    List<Payment> findByPendingAmountGreaterThan(java.math.BigDecimal amount);

    // Zero out pending balance for a user (clear pending fees action)
    @Modifying
    @Transactional
    @Query("UPDATE Payment p SET p.pendingAmount = 0 WHERE p.userId = :userId AND p.pendingAmount > 0")
    void clearPendingAmountByUserId(@Param("userId") UUID userId);

    // All successful payments within a single day — used for student drill-down
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'SUCCESS'
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        ORDER BY p.createdAt DESC
        """)
    List<Payment> findSuccessfulPaymentsForDay(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );
}