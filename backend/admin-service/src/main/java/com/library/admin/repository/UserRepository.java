package com.library.admin.repository;

import com.library.admin.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Paginated list of all students with optional account-status + membership-status filters
    // status           = null/ACTIVE/INACTIVE  → filters by user.isActive
    // membershipStatus = null/ACTIVE/INACTIVE  → filters by whether student has a live membership
    @Query("""
        SELECT u FROM User u
        WHERE u.role = 'STUDENT'
          AND (:status IS NULL
               OR (:status = 'ACTIVE'   AND u.isActive = true)
               OR (:status = 'INACTIVE' AND u.isActive = false))
          AND (:membershipStatus IS NULL
               OR (:membershipStatus = 'ACTIVE' AND EXISTS (
                       SELECT m FROM Membership m
                       WHERE m.userId = u.id
                         AND m.status = 'ACTIVE'
                         AND m.endDate >= CURRENT_DATE))
               OR (:membershipStatus = 'INACTIVE' AND NOT EXISTS (
                       SELECT m FROM Membership m
                       WHERE m.userId = u.id
                         AND m.status = 'ACTIVE'
                         AND m.endDate >= CURRENT_DATE)))
        ORDER BY u.createdAt DESC
        """)
    Page<User> findStudentsByStatus(
            @Param("status")           String status,
            @Param("membershipStatus") String membershipStatus,
            Pageable pageable
    );

    @Query("""
        SELECT u FROM User u
        WHERE u.role = 'STUDENT'
          AND u.isActive = true
          AND u.mobile IS NOT NULL
          AND EXISTS (
              SELECT m FROM Membership m
              WHERE m.userId = u.id
                AND m.status = 'ACTIVE'
                AND m.endDate >= CURRENT_DATE)
        ORDER BY u.name
        """)
    List<User> findStudentsWithActiveMemberships();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'STUDENT' AND u.isActive = true")
    long countActiveStudents();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'STUDENT'")
    long countAllStudents();

    java.util.Optional<User> findByMobile(String mobile);
}