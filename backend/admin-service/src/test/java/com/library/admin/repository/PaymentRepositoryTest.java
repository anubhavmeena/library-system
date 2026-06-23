package com.library.admin.repository;

import com.library.admin.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    PaymentRepository paymentRepository;

    private final LocalDateTime BASE = LocalDateTime.of(2025, 3, 10, 12, 0);

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private Payment save(Payment.Status status, BigDecimal amount, LocalDateTime createdAt) {
        return paymentRepository.save(Payment.builder()
                .id(UUID.randomUUID())
                .membershipId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(amount)
                .status(status)
                .createdAt(createdAt)
                .build());
    }

    private Payment saveWithPending(UUID userId, BigDecimal amount, BigDecimal pendingAmount, LocalDateTime createdAt) {
        return paymentRepository.save(Payment.builder()
                .id(UUID.randomUUID())
                .membershipId(UUID.randomUUID())
                .userId(userId)
                .amount(amount)
                .pendingAmount(pendingAmount)
                .status(Payment.Status.SUCCESS)
                .createdAt(createdAt)
                .build());
    }

    // ── sumRevenueForPeriod ──────────────────────────────────────────────────

    @Test
    void sumRevenueForPeriod_sumsOnlySuccessPayments() {
        save(Payment.Status.SUCCESS, new BigDecimal("200.00"), BASE);
        save(Payment.Status.SUCCESS, new BigDecimal("350.00"), BASE.plusHours(1));
        save(Payment.Status.FAILED,  new BigDecimal("999.00"), BASE.plusHours(2));  // excluded

        BigDecimal total = paymentRepository.sumRevenueForPeriod(
                BASE.minusHours(1), BASE.plusHours(3));

        assertThat(total).isEqualByComparingTo("550.00");
    }

    @Test
    void sumRevenueForPeriod_returnsZeroWhenNoSuccessPayments() {
        save(Payment.Status.FAILED,  new BigDecimal("100.00"), BASE);
        save(Payment.Status.PENDING, new BigDecimal("200.00"), BASE.plusHours(1));

        BigDecimal total = paymentRepository.sumRevenueForPeriod(
                BASE.minusHours(1), BASE.plusHours(3));

        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    void sumRevenueForPeriod_returnsZeroWhenTableEmpty() {
        BigDecimal total = paymentRepository.sumRevenueForPeriod(
                BASE.minusHours(1), BASE.plusHours(3));

        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    void sumRevenueForPeriod_excludesPaymentsOutsideWindow() {
        save(Payment.Status.SUCCESS, new BigDecimal("500.00"), BASE.minusDays(1)); // before window
        save(Payment.Status.SUCCESS, new BigDecimal("300.00"), BASE);               // in window

        BigDecimal total = paymentRepository.sumRevenueForPeriod(
                BASE.minusHours(1), BASE.plusHours(1));

        assertThat(total).isEqualByComparingTo("300.00");
    }

    @Test
    void sumRevenueForPeriod_excludesRefundedAndPendingStatuses() {
        save(Payment.Status.REFUNDED, new BigDecimal("100.00"), BASE);
        save(Payment.Status.PENDING,  new BigDecimal("200.00"), BASE);
        save(Payment.Status.SUCCESS,  new BigDecimal("50.00"),  BASE);

        BigDecimal total = paymentRepository.sumRevenueForPeriod(
                BASE.minusHours(1), BASE.plusHours(1));

        assertThat(total).isEqualByComparingTo("50.00");
    }

    @Test
    void sumRevenueForPeriod_inclusiveBoundaries() {
        save(Payment.Status.SUCCESS, new BigDecimal("100.00"), BASE);            // exactly at from
        save(Payment.Status.SUCCESS, new BigDecimal("200.00"), BASE.plusHours(5)); // exactly at to

        BigDecimal total = paymentRepository.sumRevenueForPeriod(BASE, BASE.plusHours(5));

        assertThat(total).isEqualByComparingTo("300.00");
    }

    // ── countSuccessfulPayments ──────────────────────────────────────────────

    @Test
    void countSuccessfulPayments_countsOnlySuccess() {
        save(Payment.Status.SUCCESS, new BigDecimal("100.00"), BASE);
        save(Payment.Status.SUCCESS, new BigDecimal("200.00"), BASE.plusHours(1));
        save(Payment.Status.FAILED,  new BigDecimal("300.00"), BASE.plusHours(2));

        long count = paymentRepository.countSuccessfulPayments(
                BASE.minusHours(1), BASE.plusHours(3));

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void countSuccessfulPayments_zeroWhenNoSuccess() {
        save(Payment.Status.PENDING, new BigDecimal("100.00"), BASE);

        long count = paymentRepository.countSuccessfulPayments(
                BASE.minusHours(1), BASE.plusHours(1));

        assertThat(count).isZero();
    }

    @Test
    void countSuccessfulPayments_zeroWhenTableEmpty() {
        long count = paymentRepository.countSuccessfulPayments(
                BASE.minusHours(1), BASE.plusHours(1));

        assertThat(count).isZero();
    }

    @Test
    void countSuccessfulPayments_excludesOutsideWindow() {
        save(Payment.Status.SUCCESS, new BigDecimal("100.00"), BASE.minusDays(1)); // outside
        save(Payment.Status.SUCCESS, new BigDecimal("200.00"), BASE);               // inside

        long count = paymentRepository.countSuccessfulPayments(
                BASE.minusHours(1), BASE.plusHours(1));

        assertThat(count).isEqualTo(1L);
    }

    // ── clearPendingAmountByUserId ───────────────────────────────────────────

    @Test
    void clearPendingAmount_foldsIntoAmountAndZerosPending() {
        UUID uid = UUID.randomUUID();
        saveWithPending(uid, new BigDecimal("1500.00"), new BigDecimal("500.00"), BASE);

        paymentRepository.clearPendingAmountByUserId(uid);

        Payment updated = paymentRepository.findByUserIdOrderByCreatedAtDesc(uid).get(0);
        assertThat(updated.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(updated.getPendingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void clearPendingAmount_clearedAmountAppearsInRevenueSums() {
        UUID uid = UUID.randomUUID();
        saveWithPending(uid, new BigDecimal("1000.00"), new BigDecimal("300.00"), BASE);

        BigDecimal before = paymentRepository.sumRevenueForPeriod(BASE.minusHours(1), BASE.plusHours(1));
        assertThat(before).isEqualByComparingTo("1000.00");

        paymentRepository.clearPendingAmountByUserId(uid);

        BigDecimal after = paymentRepository.sumRevenueForPeriod(BASE.minusHours(1), BASE.plusHours(1));
        assertThat(after).isEqualByComparingTo("1300.00");
    }
}
