package com.library.admin.repository;

import com.library.admin.entity.VisitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface VisitorEventRepository extends JpaRepository<VisitorEvent, Long> {
    long countByCreatedAtAfter(LocalDateTime since);
}
