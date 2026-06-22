package com.library.membership.repository;

import com.library.membership.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    List<Membership> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'ACTIVE' ORDER BY m.endDate DESC")
    Optional<Membership> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.status = 'QUEUED'")
    Optional<Membership> findQueuedByUserId(@Param("userId") UUID userId);

    @Query("SELECT m FROM Membership m WHERE m.seatId = :seatId AND m.status = 'ACTIVE'")
    Optional<Membership> findActiveBySeatId(@Param("seatId") UUID seatId);

    boolean existsBySeatIdAndStatus(UUID seatId, Membership.Status status);
}