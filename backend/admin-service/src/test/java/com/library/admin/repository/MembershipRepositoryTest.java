package com.library.admin.repository;

import com.library.admin.entity.Membership;
import com.library.admin.entity.SeatBooking;
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

    @Autowired
    SeatBookingRepository seatBookingRepository;

    private final UUID userId1 = UUID.randomUUID();
    private final UUID userId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        seatBookingRepository.deleteAll();
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

    // ── findFirstByUserIdAndStatusOrderByEndDateDesc ────────────────────────────────────────────────

    @Test
    void findFirstByUserIdAndStatusOrderByEndDateDesc_found() {
        Membership m = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(userId1, Membership.Status.ACTIVE);

        assertThat(result).isPresent()
                .get().extracting(Membership::getId).isEqualTo(m.getId());
    }

    @Test
    void findFirstByUserIdAndStatusOrderByEndDateDesc_notFound_wrongStatus() {
        save(userId1, Membership.Status.EXPIRED, LocalDate.now().minusDays(1), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(userId1, Membership.Status.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstByUserIdAndStatusOrderByEndDateDesc_notFound_wrongUser() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(userId2, Membership.Status.ACTIVE);

        assertThat(result).isEmpty();
    }

    // ── findFirstByUserIdCurrentOrderByEndDateDesc ──────────────────────────────
    // Regression coverage: this hit production as NonUniqueResultException when a
    // user ended up with 2 simultaneously ACTIVE memberships — a plain @Query +
    // Optional<T> silently assumes uniqueness Spring Data doesn't actually enforce.

    @Test
    void findFirstByUserIdCurrentOrderByEndDateDesc_singleActive_found() {
        Membership m = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(userId1);

        assertThat(result).isPresent()
                .get().extracting(Membership::getId).isEqualTo(m.getId());
    }

    @Test
    void findFirstByUserIdCurrentOrderByEndDateDesc_twoSimultaneousActive_doesNotThrowAndReturnsLatestEndDate() {
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);
        Membership later = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(20), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(userId1);

        assertThat(result).isPresent()
                .get().extracting(Membership::getId).isEqualTo(later.getId());
    }

    @Test
    void findFirstByUserIdCurrentOrderByEndDateDesc_activeAndGraceBothMatch_gracePreferredEvenWithEarlierEndDate() {
        // Mirrors a real production case: a student self-books a brand new ACTIVE
        // membership (ending further out) while an older one is still unresolved
        // in GRACE (ending earlier, dues owed). GRACE must win regardless of
        // endDate — an unresolved-dues row should never be masked by a newer,
        // unrelated booking.
        save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(5), false);
        Membership grace = save(userId1, Membership.Status.GRACE, LocalDate.now().minusDays(2), false);

        Optional<Membership> result = membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(userId1);

        assertThat(result).isPresent()
                .get().extracting(Membership::getId).isEqualTo(grace.getId());
    }

    @Test
    void findFirstByUserIdCurrentOrderByEndDateDesc_notFound_returnsEmpty() {
        Optional<Membership> result = membershipRepository.findFirstByUserIdCurrentOrderByEndDateDesc(userId1);

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

    // ── findBySeatNumberOrderByStartDateDesc ─────────────────────────────────

    private Membership saveWithSeat(String seatNumber, LocalDate startDate, LocalDate endDate, Membership.Status status) {
        return membershipRepository.save(Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .seatNumber(seatNumber)
                .shift("MORNING")
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .build());
    }

    @Test
    void findBySeatNumberOrderByStartDateDesc_returnsNewestFirst() {
        Membership oldest = saveWithSeat("A1", LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), Membership.Status.EXPIRED);
        Membership newest = saveWithSeat("A1", LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), Membership.Status.ACTIVE);
        saveWithSeat("B1", LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), Membership.Status.ACTIVE); // different seat

        List<Membership> result = membershipRepository.findBySeatNumberOrderByStartDateDesc("A1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newest.getId());
        assertThat(result.get(1).getId()).isEqualTo(oldest.getId());
    }

    @Test
    void findBySeatNumberOrderByStartDateDesc_noBookings_returnsEmpty() {
        List<Membership> result = membershipRepository.findBySeatNumberOrderByStartDateDesc("D26");

        assertThat(result).isEmpty();
    }

    // ── findActiveMembershipsWithoutSeatBooking ──────────────────────────────

    private SeatBooking saveBooking(UUID membershipId, SeatBooking.Status status) {
        return seatBookingRepository.save(SeatBooking.builder()
                .id(UUID.randomUUID())
                .seatId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .membershipId(membershipId)
                .shift("MORNING")
                .bookingDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(status)
                .build());
    }

    @Test
    void findActiveMembershipsWithoutSeatBooking_flagsActiveMembershipWithNoBooking() {
        Membership orphaned = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);
        // control: an ACTIVE membership WITH a matching booking must not be flagged
        Membership backed = save(userId2, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);
        saveBooking(backed.getId(), SeatBooking.Status.ACTIVE);

        List<Membership> result = membershipRepository.findActiveMembershipsWithoutSeatBooking();

        assertThat(result).extracting(Membership::getId).containsExactly(orphaned.getId());
    }

    @Test
    void findActiveMembershipsWithoutSeatBooking_bookingExistsButReleased_stillFlagged() {
        // A RELEASED (not ACTIVE) booking doesn't count as "backing" the
        // membership — matches how the rest of the app treats booking status.
        Membership mem = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);
        saveBooking(mem.getId(), SeatBooking.Status.RELEASED);

        List<Membership> result = membershipRepository.findActiveMembershipsWithoutSeatBooking();

        assertThat(result).extracting(Membership::getId).containsExactly(mem.getId());
    }

    @Test
    void findActiveMembershipsWithoutSeatBooking_ignoresNonActiveMemberships() {
        Membership grace = save(userId1, Membership.Status.GRACE, LocalDate.now().minusDays(2), false);
        Membership pending = save(userId2, Membership.Status.PENDING, LocalDate.now().plusDays(10), false);

        List<Membership> result = membershipRepository.findActiveMembershipsWithoutSeatBooking();

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveMembershipsWithoutSeatBooking_noneOrphaned_returnsEmpty() {
        Membership mem = save(userId1, Membership.Status.ACTIVE, LocalDate.now().plusDays(10), false);
        saveBooking(mem.getId(), SeatBooking.Status.ACTIVE);

        List<Membership> result = membershipRepository.findActiveMembershipsWithoutSeatBooking();

        assertThat(result).isEmpty();
    }
}
