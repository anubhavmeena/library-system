package com.library.auth.repository;

import com.library.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.mobile = :mobile OR u.email = :email")
    boolean existsByMobileOrEmail(@Param("mobile") String mobile, @Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.mobile = :contact OR u.email = :email")
    Optional<User> findByMobileOrEmail(@Param("contact") String contact, @Param("email") String email);

    Optional<User> findByMobile(String mobile);
    Optional<User> findByEmail(String email);
}