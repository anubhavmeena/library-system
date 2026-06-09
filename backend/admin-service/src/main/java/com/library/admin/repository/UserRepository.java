package com.library.admin.repository;

import com.library.admin.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Paginated list of all students with optional status filter
    // status = null   → all students
    // status = ACTIVE → isActive = true
    // status = INACTIVE → isActive = false
    @Query("""
        SELECT u FROM User u
        WHERE u.role = 'STUDENT'
          AND (:status IS NULL
               OR (:status = 'ACTIVE'   AND u.isActive = true)
               OR (:status = 'INACTIVE' AND u.isActive = false))
        ORDER BY u.createdAt DESC
        """)
    Page<User> findStudentsByStatus(
            @Param("status") String status,
            Pageable pageable
    );

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'STUDENT' AND u.isActive = true")
    long countActiveStudents();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'STUDENT'")
    long countAllStudents();
}