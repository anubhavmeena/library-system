package com.library.membership.repository;

import com.library.membership.entity.Membership;
import com.library.membership.entity.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class MembershipRepositoryTest {

    @Autowired MembershipRepository membershipRepository;
    @Autowired PlanRepository       planRepository;

    private Plan plan;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        planRepository.deleteAll();

        plan = planRepository.save(Plan.builder()
                .name("Full Day Plan")
                .planType(Plan.PlanType.FULL_DAY)
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .isActive(true)
                .build());
    }

    private Membership buildMembership(UUID uid, Membership.Status status) {
        return Membership.builder()
                .userId(uid)
                .plan(plan)
                .shift("FULL_DAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(status)
                .build();
    }

    private Membership buildMembershipWithSeat(UUID uid, UUID seatId, Membership.Status status) {
        return Membership.builder()
                .userId(uid)
                .plan(plan)
                .seatId(seatId)
                .shift("FULL_DAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(status)
                .build();
    }

    // ── findByUserIdOrderByCreatedAtDesc ──────────────────────────────────────

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsAllForUser() {
        membershipRepository.save(buildMembership(userId, Membership.Status.ACTIVE));
        membershipRepository.save(buildMembership(userId, Membership.Status.EXPIRED));

        List<Membership> result = membershipRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(m -> m.getStatus().name())
                .containsExactlyInAnyOrder("ACTIVE", "EXPIRED");
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_differentUser_returnsEmpty() {
        membershipRepository.save(buildMembership(userId, Membership.Status.ACTIVE));

        List<Membership> result = membershipRepository.findByUserIdOrderByCreatedAtDesc(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // ── findActiveByUserId ────────────────────────────────────────────────────

    @Test
    void findActiveByUserId_activeMembership_found() {
        membershipRepository.save(buildMembership(userId, Membership.Status.ACTIVE));

        Optional<Membership> result = membershipRepository.findActiveByUserId(userId);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(Membership.Status.ACTIVE);
    }

    @Test
    void findActiveByUserId_pendingMembership_returnsEmpty() {
        membershipRepository.save(buildMembership(userId, Membership.Status.PENDING));

        assertThat(membershipRepository.findActiveByUserId(userId)).isEmpty();
    }

    @Test
    void findActiveByUserId_expiredMembership_returnsEmpty() {
        membershipRepository.save(buildMembership(userId, Membership.Status.EXPIRED));

        assertThat(membershipRepository.findActiveByUserId(userId)).isEmpty();
    }

    @Test
    void findActiveByUserId_noMemberships_returnsEmpty() {
        assertThat(membershipRepository.findActiveByUserId(userId)).isEmpty();
    }

    // ── findActiveBySeatId ────────────────────────────────────────────────────

    @Test
    void findActiveBySeatId_activeBooking_found() {
        UUID seatId = UUID.randomUUID();
        membershipRepository.save(buildMembershipWithSeat(userId, seatId, Membership.Status.ACTIVE));

        Optional<Membership> result = membershipRepository.findActiveBySeatId(seatId);

        assertThat(result).isPresent();
    }

    @Test
    void findActiveBySeatId_pendingBooking_returnsEmpty() {
        UUID seatId = UUID.randomUUID();
        membershipRepository.save(buildMembershipWithSeat(userId, seatId, Membership.Status.PENDING));

        assertThat(membershipRepository.findActiveBySeatId(seatId)).isEmpty();
    }

    @Test
    void findActiveBySeatId_differentSeat_returnsEmpty() {
        UUID seatId = UUID.randomUUID();
        membershipRepository.save(buildMembershipWithSeat(userId, seatId, Membership.Status.ACTIVE));

        assertThat(membershipRepository.findActiveBySeatId(UUID.randomUUID())).isEmpty();
    }

    // ── existsBySeatIdAndStatus ───────────────────────────────────────────────

    @Test
    void existsBySeatIdAndStatus_matchExists_returnsTrue() {
        UUID seatId = UUID.randomUUID();
        membershipRepository.save(buildMembershipWithSeat(userId, seatId, Membership.Status.ACTIVE));

        assertThat(membershipRepository.existsBySeatIdAndStatus(seatId, Membership.Status.ACTIVE))
                .isTrue();
    }

    @Test
    void existsBySeatIdAndStatus_statusMismatch_returnsFalse() {
        UUID seatId = UUID.randomUUID();
        membershipRepository.save(buildMembershipWithSeat(userId, seatId, Membership.Status.PENDING));

        assertThat(membershipRepository.existsBySeatIdAndStatus(seatId, Membership.Status.ACTIVE))
                .isFalse();
    }

    @Test
    void existsBySeatIdAndStatus_seatNotBooked_returnsFalse() {
        assertThat(membershipRepository.existsBySeatIdAndStatus(UUID.randomUUID(), Membership.Status.ACTIVE))
                .isFalse();
    }
}
