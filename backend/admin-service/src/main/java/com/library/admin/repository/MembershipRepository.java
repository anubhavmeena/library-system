package com.library.admin.repository;

import com.library.admin.entity.Membership;
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

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'QUEUED'")
    Optional<Membership> findQueuedByUserId(@Param("userId") UUID userId);

    // Used by getAllStudents/getStudentDetails — a student's currently-relevant
    // membership is either ACTIVE or GRACE (lapsed, seat still held, dues owed).
    @Query("""
        SELECT m FROM Membership m
        WHERE m.userId = :userId AND m.status IN ('ACTIVE', 'GRACE')
        ORDER BY m.endDate DESC
        """)
    Optional<Membership> findFirstByUserIdCurrentOrderByEndDateDesc(@Param("userId") UUID userId);

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