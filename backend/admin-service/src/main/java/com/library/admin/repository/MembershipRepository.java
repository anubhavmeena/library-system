package com.library.admin.repository;

import com.library.admin.entity.Membership;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    // Used by getStudentDetails and getAllStudents to join active membership.
    // findFirst+OrderBy (not findBy) because a user can end up with more than
    // one ACTIVE row (e.g. overlapping renewal); take the one ending latest.
    Optional<Membership> findFirstByUserIdAndStatusOrderByEndDateDesc(UUID userId, Membership.Status status);

    // Used by getExpiringMemberships and sendBulkReminders
    // Returns ALL ACTIVE memberships expiring on or before the given date
    @Query("""
        SELECT m FROM Membership m
        WHERE m.status = 'ACTIVE'
          AND m.endDate >= CURRENT_DATE
          AND m.endDate <= :upTo
        ORDER BY m.endDate ASC
        """)
    List<Membership> findMembershipsExpiringBefore(@Param("upTo") LocalDate upTo);

    // Used by ExpiryReminderScheduler — only fetches where reminder not yet sent
    // Fires at the 7-day and 3-day checkpoints
    @Query("""
        SELECT m FROM Membership m
        WHERE m.status = 'ACTIVE'
          AND m.endDate >= :from
          AND m.endDate <= :to
          AND m.reminderSent = false
        ORDER BY m.endDate ASC
        """)
    List<Membership> findExpiringMemberships(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to
    );

    // Used by SeatExpiredScheduler — finds ACTIVE memberships whose endDate has passed
    @Query("""
        SELECT m FROM Membership m
        WHERE m.status = 'ACTIVE'
          AND m.endDate < :today
        ORDER BY m.endDate ASC
        """)
    List<Membership> findExpiredActive(@Param("today") LocalDate today);

    // Same @Query-without-limit hazard as findFirstByUserIdCurrentOrderByEndDateDesc
    // below — kept safe the same way even though a second QUEUED row shouldn't
    // normally occur (application logic guards against it at creation time).
    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'QUEUED'")
    List<Membership> findQueuedByUserId(@Param("userId") UUID userId, Pageable pageable);

    default Optional<Membership> findQueuedByUserId(UUID userId) {
        List<Membership> results = findQueuedByUserId(userId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // Used by getAllStudents/getStudentDetails — a student's currently-relevant
    // membership is either ACTIVE or GRACE (lapsed, seat still held, dues owed).
    //
    // NOTE: unlike findFirstByUserIdAndStatusOrderByEndDateDesc/
    // findFirstByUserIdOrderByEndDateDesc below (pure Spring-Data-derived
    // queries, which Spring Data auto-limits to 1 row because it recognizes the
    // "findFirst" keyword), this is a custom @Query method — Spring Data does
    // NOT apply that same auto-limiting to @Query-annotated queries. A student
    // with 2+ simultaneously ACTIVE/GRACE memberships (e.g. a duplicate
    // admin-created booking) previously caused Hibernate to throw
    // NonUniqueResultException here and take down the whole students list.
    // Backed by a Pageable-limited query instead so it's safe regardless of
    // how many rows actually match.
    @Query("""
        SELECT m FROM Membership m
        WHERE m.userId = :userId AND m.status IN ('ACTIVE', 'GRACE')
        ORDER BY m.endDate DESC
        """)
    List<Membership> findCurrentByUserIdOrderByEndDateDesc(@Param("userId") UUID userId, Pageable pageable);

    default Optional<Membership> findFirstByUserIdCurrentOrderByEndDateDesc(UUID userId) {
        List<Membership> results = findCurrentByUserIdOrderByEndDateDesc(userId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // Used by getSeatMap — seats occupied by an ACTIVE membership (date-bound) or a
    // GRACE membership (held indefinitely until an admin releases it).
    @Query("""
        SELECT m FROM Membership m
        WHERE m.seatNumber IS NOT NULL
          AND (m.status = 'GRACE' OR (m.status = 'ACTIVE' AND m.endDate <= :upTo))
        """)
    List<Membership> findOccupyingSeatMemberships(@Param("upTo") LocalDate upTo);

    @Query("SELECT COUNT(m) FROM Membership m WHERE m.status = 'ACTIVE'")
    long countActiveMemberships();

    @Query("SELECT COUNT(m) FROM Membership m WHERE m.status = 'EXPIRED'")
    long countExpiredMemberships();

    boolean existsByUserId(UUID userId);

    Optional<Membership> findFirstByUserIdOrderByEndDateDesc(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Membership m WHERE m.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}