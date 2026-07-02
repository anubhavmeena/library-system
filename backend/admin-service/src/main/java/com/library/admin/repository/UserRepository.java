package com.library.admin.repository;

import com.library.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Broadcast recipients — students with a currently live ACTIVE membership and a mobile number
    @Query("""
        SELECT u FROM User u
        WHERE u.role = 'STUDENT'
          AND u.mobile IS NOT NULL
          AND EXISTS (
              SELECT m FROM Membership m
              WHERE m.userId = u.id
                AND m.status = 'ACTIVE'
                AND m.endDate >= CURRENT_DATE)
        ORDER BY u.name
        """)
    List<User> findStudentsWithActiveMemberships();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'STUDENT'")
    long countAllStudents();

    java.util.Optional<User> findByMobile(String mobile);
}