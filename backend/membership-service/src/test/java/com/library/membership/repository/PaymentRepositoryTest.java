package com.library.membership.repository;

import com.library.membership.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired PaymentRepository paymentRepository;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private Payment buildPayment(UUID membershipId, String orderId) {
        return Payment.builder()
                .membershipId(membershipId)
                .userId(userId)
                .amount(BigDecimal.valueOf(600))
                .gatewayOrderId(orderId)
                .status(Payment.Status.PENDING)
                .build();
    }

    // ── findByGatewayOrderId ──────────────────────────────────────────────────

    @Test
    void findByGatewayOrderId_found() {
        paymentRepository.save(buildPayment(UUID.randomUUID(), "order_abc123"));

        Optional<Payment> result = paymentRepository.findByGatewayOrderId("order_abc123");

        assertThat(result).isPresent();
        assertThat(result.get().getGatewayOrderId()).isEqualTo("order_abc123");
    }

    @Test
    void findByGatewayOrderId_notFound_returnsEmpty() {
        assertThat(paymentRepository.findByGatewayOrderId("non-existent")).isEmpty();
    }

    // ── findByUserIdOrderByCreatedAtDesc ──────────────────────────────────────

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsAllForUser() {
        paymentRepository.save(buildPayment(UUID.randomUUID(), "order_1"));
        paymentRepository.save(buildPayment(UUID.randomUUID(), "order_2"));

        List<Payment> result = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_differentUser_returnsEmpty() {
        paymentRepository.save(buildPayment(UUID.randomUUID(), "order_3"));

        List<Payment> result = paymentRepository.findByUserIdOrderByCreatedAtDesc(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // ── findByMembershipId ────────────────────────────────────────────────────

    @Test
    void findByMembershipId_found() {
        UUID membershipId = UUID.randomUUID();
        paymentRepository.save(buildPayment(membershipId, "order_mem1"));

        Optional<Payment> result = paymentRepository.findByMembershipId(membershipId);

        assertThat(result).isPresent();
        assertThat(result.get().getMembershipId()).isEqualTo(membershipId);
    }

    @Test
    void findByMembershipId_notFound_returnsEmpty() {
        assertThat(paymentRepository.findByMembershipId(UUID.randomUUID())).isEmpty();
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Test
    void save_createdAtSetByPrePersist() {
        Payment saved = paymentRepository.save(buildPayment(UUID.randomUUID(), "order_lifecycle"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
