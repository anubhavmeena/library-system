package com.library.membership.repository;

import com.library.membership.entity.Membership;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    // Includes PENDING/CANCELLED rows by design — used for the student's full
    // membership history display (getUserMemberships). Do NOT reuse this for
    // "what's their last meaningful membership" fallback logic — see
    // findFirstByUserIdAndStatusNotOrderByCreatedAtDesc below for that.
    List<Membership> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Used by getMyDisplayStatus's "no current membership" fallback — the
    // student's last MEANINGFUL membership, excluding PENDING (an abandoned/
    // never-completed checkout attempt isn't real history — a student who
    // retried a failed payment several times could leave newer-dated PENDING
    // duds that beat a real EXPIRED/CANCELLED row here, making
    // StudentStatusResolver wrongly resolve NEW instead of RELEASED). Mirrors
    // admin-service's identically-purposed repository method.
    Optional<Membership> findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(UUID userId, Membership.Status status);

    // NOTE: these are custom @Query methods, not Spring-Data-derived "findFirst"
    // queries — Spring Data does NOT auto-limit @Query results to one row just
    // because the return type is Optional<T>. A user with 2+ rows matching (e.g.
    // 2 simultaneously ACTIVE memberships from a duplicate booking) makes
    // Hibernate throw NonUniqueResultException instead of just picking one.
    // Backed by Pageable-limited queries so each stays safe regardless of how
    // many rows actually match, same fix as admin-service's equivalent method.

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'ACTIVE' ORDER BY m.endDate DESC")
    List<Membership> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    default Optional<Membership> findActiveByUserId(UUID userId) {
        return firstOrEmpty(findActiveByUserId(userId, PageRequest.of(0, 1)));
    }

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'QUEUED'")
    List<Membership> findQueuedByUserId(@Param("userId") UUID userId, Pageable pageable);

    default Optional<Membership> findQueuedByUserId(UUID userId) {
        return firstOrEmpty(findQueuedByUserId(userId, PageRequest.of(0, 1)));
    }

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'GRACE'")
    List<Membership> findGraceByUserId(@Param("userId") UUID userId, Pageable pageable);

    default Optional<Membership> findGraceByUserId(UUID userId) {
        return firstOrEmpty(findGraceByUserId(userId, PageRequest.of(0, 1)));
    }

    @Query("SELECT m FROM Membership m WHERE m.seatId = :seatId AND m.status = 'ACTIVE'")
    List<Membership> findActiveBySeatId(@Param("seatId") UUID seatId, Pageable pageable);

    default Optional<Membership> findActiveBySeatId(UUID seatId) {
        return firstOrEmpty(findActiveBySeatId(seatId, PageRequest.of(0, 1)));
    }

    private static Optional<Membership> firstOrEmpty(List<Membership> results) {
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    boolean existsBySeatIdAndStatus(UUID seatId, Membership.Status status);

    // gatewayOrderId is unique per in-flight checkout (set once by createOrder()/
    // createDuesOrder(), never shared across memberships), so a plain derived
    // Optional lookup is safe here unlike the userId-scoped queries above.
    Optional<Membership> findByGatewayOrderId(String gatewayOrderId);
}
