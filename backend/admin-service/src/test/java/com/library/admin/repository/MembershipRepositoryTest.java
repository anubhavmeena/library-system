package com.library.admin.repository;

import com.library.admin.entity.Membership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class MembershipRepositoryTest {

    @Autowired
    MembershipRepository membershipRepository;

    private final UUID userId1 = UUID.randomUUID();
    private final UUID userId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
    }

    private Membership save(UUID userId, Membership.Status status,
                            LocalDate endDate, boolean reminderSent) {
        return membershipRepository.save(Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planId(UUID.randomUUID())
                .seatNumber("A1")
                .shift("MORNING")
                .startDate(LocalDate.now().minusDays(5))
                .endDate(endDate)
                .status(status)
                .reminderSent(reminderSent)
                .build());
    }

    // ── findByUserIdAndStatus ────────────────────────────────────────────────

    @Test
    void findByUserIdAndStatus_found() {
        Membership m = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);

        Optional<Membership> result = membershipRepository.findByUserIdAndStatus(userId1, Membership.Status.ACTIVE);

        assertThat(result).isPresent()
                .get().extracting(Membership::getId).isEqualTo(m.getId());
    }

    @Test
    void findByUserIdAndStatus_notFound_wrongStatus() {
        save(userId1, Membership.Status.EXPIRED, LocalDate.now().minusDays(1), false);

        Optional<Membership> result = membershipRepository.findByUserIdAndStatus(userId1, Membership.Status.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserIdAndStatus_notFound_wrongUser() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);

        Optional<Membership> result = membershipRepository.findByUserIdAndStatus(userId2, Membership.Status.ACTIVE);

        assertThat(result).isEmpty();
    }

    // ── findMembershipsExpiringBefore ────────────────────────────────────────
    // Returns ACTIVE memberships where endDate >= CURRENT_DATE AND endDate <= upTo

    @Test
    void findMembershipsExpiringBefore_returnsInRange() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(3), false);
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(6), false);

        List<Membership> result = membershipRepository.findMembershipsExpiringBefore(LocalDate.now().plusDays(7));

        assertThat(result).hasSize(2);
    }

    @Test
    void findMembershipsExpiringBefore_excludesBeyondUpTo() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false); // beyond 7-day window
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);

        List<Membership> result = membershipRepository.findMembershipsExpiringBefore(LocalDate.now().plusDays(7));

        assertThat(result).hasSize(1);
    }

    @Test
    void findMembershipsExpiringBefore_excludesExpiredMemberships() {
        save(userId1, Membership.Status.EXPIRED, LocalDate.now().plusDays(3), false);

        List<Membership> result = membershipRepository.findMembershipsExpiringBefore(LocalDate.now().plusDays(7));

        assertThat(result).isEmpty();
    }

    @Test
    void findMembershipsExpiringBefore_excludesPastEndDates() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().minusDays(1), false); // already expired

        List<Membership> result = membershipRepository.findMembershipsExpiringBefore(LocalDate.now().plusDays(7));

        assertThat(result).isEmpty();
    }

    @Test
    void findMembershipsExpiringBefore_orderByEndDateAsc() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(6), false);
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(2), false);

        List<Membership> result = membershipRepository.findMembershipsExpiringBefore(LocalDate.now().plusDays(7));

        assertThat(result.get(0).getEndDate()).isBefore(result.get(1).getEndDate());
    }

    // ── findExpiringMemberships ──────────────────────────────────────────────
    // Returns ACTIVE + reminderSent=false + endDate in [from, to]

    @Test
    void findExpiringMemberships_returnsReminderNotSent() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);

        List<Membership> result = membershipRepository.findExpiringMemberships(
                LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(result).hasSize(1);
    }

    @Test
    void findExpiringMemberships_excludesAlreadySent() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), true); // reminderSent=true

        List<Membership> result = membershipRepository.findExpiringMemberships(
                LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(result).isEmpty();
    }

    @Test
    void findExpiringMemberships_excludesOutsideDateRange() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false); // outside range

        List<Membership> result = membershipRepository.findExpiringMemberships(
                LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(result).isEmpty();
    }

    @Test
    void findExpiringMemberships_excludesNonActive() {
        save(userId1, Membership.Status.CANCELLED, LocalDate.now().plusDays(3), false);

        List<Membership> result = membershipRepository.findExpiringMemberships(
                LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(result).isEmpty();
    }

    @Test
    void findExpiringMemberships_mixedReminderFlags_returnsOnlyUnsentOnes() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(3), false);
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), true); // already sent

        List<Membership> result = membershipRepository.findExpiringMemberships(
                LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId1);
    }

    // ── countActiveMemberships / countExpiredMemberships ────────────────────

    @Test
    void countActiveMemberships_correctCount() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);
        save(UUID.randomUUID(), Membership.Status.EXPIRED, LocalDate.now().minusDays(1), false);

        assertThat(membershipRepository.countActiveMemberships()).isEqualTo(2L);
    }

    @Test
    void countActiveMemberships_zeroWhenEmpty() {
        assertThat(membershipRepository.countActiveMemberships()).isZero();
    }

    @Test
    void countExpiredMemberships_correctCount() {
        save(userId1, Membership.Status.EXPIRED, LocalDate.now().minusDays(5), false);
        save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);

        assertThat(membershipRepository.countExpiredMemberships()).isEqualTo(1L);
    }

    @Test
    void countExpiredMemberships_zeroWhenEmpty() {
        assertThat(membershipRepository.countExpiredMemberships()).isZero();
    }
}
