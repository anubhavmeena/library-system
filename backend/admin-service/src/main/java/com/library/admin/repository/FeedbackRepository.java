package com.library.admin.repository;

import com.library.admin.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    @Query("SELECT f FROM Feedback f LEFT JOIN FETCH f.user ORDER BY f.createdAt DESC")
    List<Feedback> findAllWithUser();

    @Query("SELECT f FROM Feedback f LEFT JOIN FETCH f.user WHERE f.type = :type ORDER BY f.createdAt DESC")
    List<Feedback> findByTypeWithUser(@Param("type") Feedback.Type type);

    @Query("SELECT f FROM Feedback f LEFT JOIN FETCH f.user WHERE f.status = :status ORDER BY f.createdAt DESC")
    List<Feedback> findByStatusWithUser(@Param("status") Feedback.Status status);

    @Query("SELECT f FROM Feedback f LEFT JOIN FETCH f.user WHERE f.type = :type AND f.status = :status ORDER BY f.createdAt DESC")
    List<Feedback> findByTypeAndStatusWithUser(
            @Param("type")   Feedback.Type type,
            @Param("status") Feedback.Status status);
}
