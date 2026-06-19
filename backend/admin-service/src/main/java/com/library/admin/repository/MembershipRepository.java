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

    // Used by getStudentDetails and getAllStudents to join active membership
    Optional<Membership> findByUserIdAndStatus(UUID userId, Membership.Status status);

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

    @Query("SELECT COUNT(m) FROM Membership m WHERE m.status = 'ACTIVE'")
    long countActiveMemberships();

    @Query("SELECT COUNT(m) FROM Membership m WHERE m.status = 'EXPIRED'")
    long countExpiredMemberships();

    @Modifying
    @Transactional
    @Query("DELETE FROM Membership m WHERE m.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}